package subprojects.release

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import subprojects.*
import subprojects.build.generator.PublishPluginRegistry

var samplesBuild: BuildType? = null
var apiBuild: BuildType? = null
var docSamplesBuild: BuildType? = null
var publishAllBuild: BuildType? = null
var publishAllEAPBuild: BuildType? = null

fun BuildType.createDeploymentBuild(id: String, name: String, description: String, versionPattern: String) {
    id(id)
    this.name = name
    this.description = description
    type = BuildTypeSettings.Type.DEPLOYMENT
    buildNumberPattern = versionPattern
    maxRunningBuilds = 1
    features {
        perfmon { }
    }
}

object ReleaseBuild : BuildType({
    createDeploymentBuild(
        "KtorReleaseAllBuild",
        "Release All",
        "Publish all artifacts and release documentation",
        ""
    )

    vcs {
        root(VCSSamples)
        root(VCSDocs)
        root(VCSCore)
    }

    val releaseBuilds = listOf(
        samplesBuild,
        apiBuild,
        docSamplesBuild,
        publishAllBuild,
    )

    params {
        text("reverse.dep.*.releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    features {
        vcsLabeling {
            vcsRootId = "${VCSCore.id}"
            labelingPattern = "%reverse.dep.*.releaseVersion%"
            successfulOnly = true
            branchFilter = BranchFilter.ReleaseBranches
        }
    }

    dependencies {
        releaseBuilds.forEach {
            if (it != null) {
                snapshot(it) {
                }
            }
        }
        snapshot(PublishPluginRegistry) {
            onDependencyFailure = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
    }
})
