package subprojects.build.libcurl

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object ProjectLibcurlBuild : Project({
    id("ProjectKtorLibcurlBuild")
    name = "Libcurl"
    description = "Build configurations for libcurl library"

    for (build in LibcurlBuilds) buildType(build)

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
        }

        defaultBuildFeatures()
    }
})

private val MacOSX64Build = LibcurlBuild(NativeEntry.MacOSX64)
private val MacOSArm64Build = LibcurlBuild(NativeEntry.MacOSArm64)
private val LinuxX64Build = LibcurlBuild(NativeEntry.LinuxX64)
private val LinuxArm64Build = LibcurlBuild(NativeEntry.LinuxX64, "-Pvcpkg.target=linux_arm64")
private val MingwX64Build = LibcurlBuild(NativeEntry.MingwX64)
private val LibcurlBuilds = listOf(MacOSX64Build, MacOSArm64Build, LinuxX64Build, LinuxArm64Build, MingwX64Build)

private fun LibcurlBuild(entry: NativeEntry, params: String = "") = BuildType({
    id("LibcurlBuild${entry.id}")
    name = "Libcurl ${entry.os.id} ${entry.arch}"

    artifactRules = """
        ktor-client/ktor-client-curl/desktop/interop/include/* => include.zip
        ktor-client/ktor-client-curl/desktop/interop/lib/${entry.target} => lib-${entry.target}.zip
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
