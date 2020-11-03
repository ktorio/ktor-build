package subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import subprojects.*

var samplesBuild: BuildType? = null
var apiBuild: BuildType? = null
var docSamplesBuild: BuildType? = null
var jvmBuild: BuildType? = null
var jsBuild: BuildType? = null
var nativeWindowsBuild: BuildType? = null
var nativeLinuxBuild: BuildType? = null
var nativeMacOSBuild: BuildType? = null
var publishAllBuild: BuildType? = null

object ReleaseBuild : BuildType({
    id("KtorReleaseAllBuild")
    name = "Release All"
    description = "Publish all artifacts and release documentation"
    type = Type.DEPLOYMENT
    buildNumberPattern = "%releaseVersion%"
    maxRunningBuilds = 1

    vcs {
        root(VCSSamples)
        root(VCSDocs)
        root(VCSCore)
    }

    val releaseBuilds = listOf(
        samplesBuild,
        apiBuild,
        docSamplesBuild,
        publishAllBuild
    )

    features {
        vcsLabeling {
            vcsRootId = "${VCSCore.id}"
            labelingPattern = "%releaseVersion%"
            successfulOnly = true
            branchFilter = "+:$defaultBranch"
        }
    }

    dependencies {
        releaseBuilds.forEach {
            if (it != null) {
                snapshot(it) {
                }
            }
        }
    }
})