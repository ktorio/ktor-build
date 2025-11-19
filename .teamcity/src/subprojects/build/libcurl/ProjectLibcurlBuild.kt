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

    for (build in LibcurlBuilds) buildType(build)

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

        defaultBuildFeatures(VCSCore.id.toString())
    }
})

private val MacOSX64Build = LibcurlBuild(NativeEntry.MacOSX64)
private val MacOSArm64Build = LibcurlBuild(NativeEntry.MacOSArm64)
private val LinuxX64Build = LibcurlBuild(NativeEntry.LinuxX64)
private val LinuxArm64Build = LibcurlBuild(NativeEntry.LinuxArm64)
private val MingwX64Build = LibcurlBuild(NativeEntry.MingwX64)
private val LibcurlBuilds = listOf(MacOSX64Build, MacOSArm64Build, LinuxX64Build, LinuxArm64Build, MingwX64Build)

private fun LibcurlBuild(entry: NativeEntry) = BuildType({
    id("LibcurlBuild${entry.id}")
    name = "Libcurl ${entry.os.id} ${entry.arch}"

    artifactRules = "ktor-client/ktor-client-curl/desktop/interop/lib/${entry.target} => libcurl-${entry.target}.tar.gz"

    vcs {
        root(VCSCore)
    }

    requirements {
        agent(entry)
    }

    params {
        param("env.VCPKG_ROOT", "%system.teamcity.build.checkoutDir%/vcpkg")
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
            tasks = entry.targetTask(":ktor-client-curl:libcurlUpdate")
            jdkHome = Env.JDK_LTS
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())
})
