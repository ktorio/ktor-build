package subprojects.build.libcurl

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*
import java.util.Locale.*

object ProjectLibcurlBuild : Project({
    id("ProjectKtorLibcurlBuild")
    name = "Libcurl"
    description = "Build configurations for libcurl library"

    for (build in LibcurlBuilds) buildType(build)
    buildType(LibcurlPushChanges)

    params {
        defaultGradleParams()
    }

    buildType {
        id("LibcurlBuild")
        name = "Build libcurl for All Platforms"
        type = BuildTypeSettings.Type.COMPOSITE

        vcs {
            root(VCSCore)
        }

        dependencies {
            for (build in LibcurlBuilds) snapshot(build) {}
            snapshot(LibcurlPushChanges) {}
        }

        defaultBuildFeatures()
    }
})

private val MacOSX64Build = LibcurlBuild(NativeEntry.MacOSX64)
private val MacOSArm64Build = LibcurlBuild(NativeEntry.MacOSArm64)
private val LinuxX64Build = LibcurlBuild(NativeEntry.LinuxX64)
private val LinuxArm64Build = LibcurlBuild(
    NativeEntry.LinuxX64,
    target = "linuxArm64",
    params = "-Pvcpkg.target=linux_arm64"
)
private val MingwX64Build = LibcurlBuild(NativeEntry.MingwX64)
private val LibcurlBuilds = listOf(MacOSX64Build, MacOSArm64Build, LinuxX64Build, LinuxArm64Build, MingwX64Build)

private fun LibcurlBuild(
    entry: NativeEntry,
    target: String = entry.target,
    params: String = ""
) = BuildType({
    id("LibcurlBuild${target.replaceFirstChar { it.titlecase(getDefault()) }}")
    name = "Libcurl $target"

    artifactRules = """
        ktor-client/ktor-client-curl/desktop/interop/include => result.zip!include
        ktor-client/ktor-client-curl/desktop/interop/lib/${target} => result.zip!lib/${target}
        %env.VCPKG_ROOT%/**/*.log
    """.trimIndent()

    vcs {
        root(VCSCore)
    }

    requirements {
        agent(entry)
    }

    params {
        param("env.VCPKG_ROOT", "%system.teamcity.build.checkoutDir%%teamcity.agent.jvm.file.separator%vcpkg")
        param("env.VCPKG_KEEP_ENV_VARS", "VCPKG_ROOT;TOOLCHAIN_LLVM_HOME;TOOLCHAIN_SYSROOT;TOOLCHAIN_TRIPLE;TOOLCHAIN_LINKER;TOOLCHAIN_GCC_TOOLCHAIN;TOOLCHAIN_LIBGCC")
    }

    steps {
        platformScript(
            name = "Install vcpkg",
            os = entry.os,
            unixScript = "install_vcpkg.sh",
            windowsScript = "install_vcpkg.ps1",
        )

        gradle {
            name = "Update libcurl"
            tasks = ":ktor-client-curl:libcurlUpdate"
            gradleParams = "--info $params"
            jdkHome = Env.JDK_LTS
        }
    }

    features {
        perfmon {}
    }
})

private val LibcurlPushChanges = BuildType({
    id("LibcurlPushChanges")
    name = "Push libcurl Changes"

    vcs {
        root(VCSCore)
        checkoutMode = CheckoutMode.ON_AGENT
    }

    requirements {
        agent(NativeEntry.LinuxArm64, Agents.ANY)
    }

    params {
        param("env.GITHUB_USER", VCSUsername)
        password("env.GITHUB_PASSWORD", VCSToken)
    }

    dependencies {
        for (build in LibcurlBuilds) {
            dependency(build) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                }
                artifacts {
                    artifactRules = "result.zip!** => ktor-client/ktor-client-curl/desktop/interop/"
                }
            }
        }
    }

    steps {
        script {
            name = "Push changes"
            scriptContent = bashScript("""
                BRANCH_NAME="teamcity/libcurl-update"

                git config user.name "TeamCity"
                git config user.email "teamcity@jetbrains.com"
                git remote set-url origin "https://${'$'}{GITHUB_USER}:${'$'}{GITHUB_PASSWORD}@github.com/ktorio/ktor.git"

                git switch --create ${'$'}BRANCH_NAME

                git add ktor-client/ktor-client-curl/
                if git diff --cached --quiet; then
                    echo "No changes to commit"
                    exit 0
                fi

                git commit -m "Update libcurl binaries"
                git push origin ${'$'}BRANCH_NAME
            """)
        }
    }

    features {
        perfmon {}
    }
})
