package subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.*

var samplesBuild: BuildType? = null
var apiBuild: BuildType? = null
var docSamplesBuild: BuildType? = null
var jvmBuild: BuildType? = null
var jsBuild: BuildType? = null
var nativeWindowsBuild: BuildType? = null
var nativeLinuxBuild: BuildType? = null
var nativeMacOSBuild: BuildType? = null


object ProjectReleaseAll : Project({
    id("ProjectReleaseKtor")
    name = "Release All"
    description = " The Full Monty! - Release Ktor framework, update docs, site, etc."

    params {
        defaultTimeouts()
    }

    buildType(ReleaseBuild)
})

object ReleaseBuild : BuildType({
    id("KtorReleaseAllBuild")
    name = "Release All"
    description = "Publish all artifacts and release documentation"

    vcs {
        root(VCSSamples)
        root(VCSDocs)
        root(VCSCore)
    }

    val releaseBuilds = listOf(
        samplesBuild,
        apiBuild,
        docSamplesBuild,
        jvmBuild,
        jsBuild,
        nativeWindowsBuild,
        nativeLinuxBuild,
        nativeMacOSBuild
    )

    dependencies {
        releaseBuilds.forEach {
            if (it != null) {
                println("Adding ${it.id}")
                snapshot(it) {
                }
            }
        }
    }
})