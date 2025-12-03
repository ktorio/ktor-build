package subprojects.build.libcurl

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object ProjectLibcurlBuild : Project({
    id("ProjectKtorLibcurlBuild")
    name = "Libcurl"
    description = "Build configurations for libcurl library"

    buildType(ExtractKotlinVersion)
    buildType(DownloadKonanToolchain)
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
private val LinuxArm64Build = LibcurlBuild(NativeEntry.LinuxArm64)
private val MingwX64Build = LibcurlBuild(NativeEntry.MingwX64)
private val LibcurlBuilds = listOf(MacOSX64Build, MacOSArm64Build, LinuxX64Build, LinuxArm64Build, MingwX64Build)

private const val KONAN_DEPENDENCIES_PATH = "%teamcity.agent.jvm.user.home%/.konan/dependencies"
private const val TOOLCHAIN_ZIP_PATH = "toolchain.zip"

// Build number that doesn't depend on the current build properties, so it can be referenced from other builds
private val KotlinToolchainBuildNumber = ExtractKotlinVersion.kotlinVersion.ref

private fun LibcurlBuild(entry: NativeEntry) = BuildType({
    id("LibcurlBuild${entry.id}")
    name = "Libcurl ${entry.os.id} ${entry.arch}"

    artifactRules = """
        ktor-client/ktor-client-curl/desktop/interop/lib/${entry.target} => libcurl-${entry.target}.zip
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
        param("env.VCPKG_KEEP_ENV_VARS", "VCPKG_ROOT;TOOLCHAIN_DIR")
    }

    // Use Konan toolchain for Linux and Windows build
    if (entry.os == Agents.OS.Linux || entry.os == Agents.OS.Windows) {
        dependencies {
            snapshot(ExtractKotlinVersion) { onDependencyFailure = FailureAction.FAIL_TO_START }
            dependency(DownloadKonanToolchain) {
                // The build can be canceled if the toolchain has already been downloaded
                snapshot { onDependencyCancel = FailureAction.IGNORE }
                artifacts {
                    buildRule = build(KotlinToolchainBuildNumber)
                    artifactRules = "+:$TOOLCHAIN_ZIP_PATH!** => $KONAN_DEPENDENCIES_PATH"
                }
            }
        }
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
            gradleParams = "--info"
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures()
})

private object ExtractKotlinVersion : BuildType({
    id("ExtractKotlinVersion")
    name = "Extract Kotlin Version"

    vcs {
        root(VCSCore, "+:gradle/libs.versions.toml")
    }

    requirements {
        agent(Agents.OS.Linux, osArch = null, hardwareCapacity = Agents.ANY)
    }

    outputParams {
        param("kotlinVersion", "%KOTLIN_VERSION%")
    }

    steps {
        script {
            name = "Extract Kotlin Version"
            scriptFile("extract_kotlin_version.sh")
        }
    }
})

private val ExtractKotlinVersion.kotlinVersion get() = depParamRefs["kotlinVersion"]

private object DownloadKonanToolchain : BuildType({
    id("DownloadKonanToolchain")
    name = "Download Konan Toolchain"
    buildNumberPattern = "${ExtractKotlinVersion.kotlinVersion}-%build.counter%"
    maxRunningBuilds = 1

    vcs {
        root(VCSCore)
    }

    requirements {
        // Use Windows agent as we want to download LLVM for Windows
        agent(Agents.OS.Windows, Agents.Arch.X64, hardwareCapacity = Agents.ANY)
    }

    dependencies {
        snapshot(ExtractKotlinVersion) { onDependencyFailure = FailureAction.FAIL_TO_START }
    }

    steps {
        python {
            name = "Check for existing build"

            val locator = "buildType:%system.teamcity.buildType.id%,number:$KotlinToolchainBuildNumber,defaultFilter:false,status:SUCCESS,count:1"
            command = script {
                content = pythonScript(
                    """
                    import urllib.request
                    import base64

                    auth = base64.b64encode(b'%system.teamcity.auth.userId%:%system.teamcity.auth.password%').decode('ascii')
                    req = urllib.request.Request('%teamcity.serverUrl%/app/rest/builds?locator=$locator')
                    req.add_header('Authorization', f'Basic {auth}')

                    with urllib.request.urlopen(req) as response:
                        builds = response.read().decode('utf-8')

                    print(builds)

                    # Check if a successful final build for this Kotlin version already exists
                    if 'count="1"' in builds:
                        print("##teamcity[buildStop comment='Toolchain is already downloaded' readdToQueue='false']")
                    """
                )
            }
        }

        gradle {
            name = "Download Kotlin Native Distribution"
            tasks = ":ktor-client-curl:downloadKotlinNativeDistribution"
            jdkHome = Env.JDK_LTS
        }

        python {
            name = "Mark the build as final"
            command = script {
                content = pythonScript(
                    """
                    print("##teamcity[buildNumber '$KotlinToolchainBuildNumber']")
                    """
                )
            }
        }
    }

    artifactRules = """
        $KONAN_DEPENDENCIES_PATH/*-glibc-*/** => $TOOLCHAIN_ZIP_PATH
        $KONAN_DEPENDENCIES_PATH/llvm-*/** => $TOOLCHAIN_ZIP_PATH
    """.trimIndent()
    publishArtifacts = PublishMode.SUCCESSFUL

    cleanup {
        // Preserve toolchains for the last three Kotlin versions
        artifacts(builds = 3)
    }
})
