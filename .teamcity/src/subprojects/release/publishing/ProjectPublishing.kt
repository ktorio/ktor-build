package subprojects.release.publishing

import dsl.scriptFile
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.gitHubAppBuildScopedToken
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import subprojects.*
import subprojects.build.core.*
import subprojects.release.*

object ProjectPublishing : Project({
    id("ProjectKtorPublishing")
    name = "Publishing"
    description = "Publish artifacts to repositories"

    val builds = listOf(
        PublishJvmToMaven,
        PublishJSToMaven,
        PublishWindowsNativeToMaven,
        PublishLinuxNativeToMaven,
        PublishMacOSNativeToMaven,
        PublishAndroidNativeToMaven,
    )
    builds.forEach(::buildType)

    buildType(PublishCustomTaskToMaven)

    params {
        configureReleaseVersion()
    }

    val waitForMaven = buildType {
        id("KtorPublish_WaitForMavenArtifacts")
        name = "Wait for Maven artifacts"
        description = "Polls the repository until the published version is resolvable"

        vcs { root(VCSCore) }

        params {
            param("env.MAVEN_VERSION", "%releaseVersion%")
            param("env.MAVEN_REPO_URL", "https://repo1.maven.org/maven2")
        }

        requirements {
            agent(Agents.OS.Linux)
        }

        steps {
            script {
                name = "Wait for artifacts"
                scriptFile("wait_for_maven.sh")
            }
        }

        dependencies {
            builds.forEach {
                snapshot(it) {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }

        failureConditions {
            executionTimeoutMin = 200
        }
    }

    val tagRelease = buildType {
        id("KtorPublish_TagRelease")
        name = "Tag release commit"

        vcs { root(VCSCore) }

        params {
            param("env.RELEASE_VERSION", "%releaseVersion%")
            param("env.RELEASE_SHA", "%build.vcs.number%")
            param("env.GITHUB_REPOSITORY", "ktorio/ktor")
        }

        features {
            gitHubAppBuildScopedToken {
                parameterName = "env.GITHUB_TOKEN"
                connectionId = "PROJECT_EXT_7"
                targetRepositories = "ktor"
            }
        }

        requirements {
            agent(Agents.OS.Linux)
        }

        steps {
            script {
                name = "Create release tag"
                scriptFile("create_tag.sh")
            }
        }
    }

    val githubRelease = buildType {
        id("KtorPublish_GitHubRelease")
        name = "Create GitHub release"
        description = "Creates a GitHub release from the Changelog"

        vcs {
            root(VCSCore)
        }

        params {
            param("env.RELEASE_VERSION", "%releaseVersion%")
            param("env.GITHUB_REPOSITORY", "ktorio/ktor")
        }

        features {
            gitHubAppBuildScopedToken {
                parameterName = "env.GITHUB_TOKEN"
                connectionId = "PROJECT_EXT_7"
                targetRepositories = "ktor"
            }
        }

        requirements {
            agent(Agents.OS.Linux)
        }

        steps {
            script {
                name = "Create Github release"
                scriptFile("create_github_release.sh")
            }
        }

        dependencies {
            snapshot(waitForMaven) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
            snapshot(tagRelease) {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    publishAllBuild = buildType {
        createCompositeBuild(
            "KtorPublish_All",
            "Publish All",
            VCSCore,
            builds + tagRelease + waitForMaven + githubRelease,
            withTrigger = TriggerType.NONE,
            buildNumber = releaseVersion,
        )

        params {
            param("reverse.dep.*.releaseVersion", "%releaseVersion%")
        }
    }
})
