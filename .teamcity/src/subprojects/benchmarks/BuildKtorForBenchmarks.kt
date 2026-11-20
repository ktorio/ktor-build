package subprojects.benchmarks

import dsl.bashScript
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ReuseBuilds
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import subprojects.Agents
import subprojects.Env
import subprojects.VCSCore
import subprojects.agent
import subprojects.build.defaultBuildFeatures
import subprojects.build.gradle.GradleUserHome
import subprojects.build.gradle.useGradleEnvironment
import subprojects.release.publishing.EXCLUDE_DOKA_GENERATION
import subprojects.release.publishing.JVM_AND_COMMON_PUBLISH_TASK

internal const val MAVEN_LOCAL_PATH = "%system.teamcity.build.checkoutDir%/ktor-repository"

internal object BuildKtorForBenchmarks : BuildType({
    id("BuildKtorForAllocationBenchmarks")
    name = "Build Ktor for benchmarks"

    vcs {
        root(VCSCore)
    }

    artifactRules = "+:ktor-repository => ktor-repository.zip"

    // The configuration cache can't be reused as the version passed to Gradle is unique for each build
    useGradleEnvironment(Agents.OS.Linux, GradleUserHome.Light, configurationCache = false)

    steps {
        script {
            name = "Checkout requested Ktor branch"
            scriptContent = bashScript(
                """
                branch="%ktor.branch%"
                if [ -n "${'$'}branch" ]; then
                    git fetch origin "${'$'}branch"
                    git checkout -f FETCH_HEAD
                fi
                """
            )
        }
        script {
            name = "Set Ktor benchmark version"
            scriptContent = bashScript(
                """
                ktorVersion="${'$'}(<VERSION)"
                ktorVersion="${'$'}{ktorVersion%-SNAPSHOT}-BENCHMARKS.%build.counter%"
                echo "##teamcity[buildNumber '${'$'}ktorVersion']"
                """
            )
        }
        script {
            // Benchmarks use only JVM artifacts
            name = "Disable native targets"
            scriptContent = bashScript(
                """
                echo "target.posix=false" >> gradle.properties
                """
            )
        }
        gradle {
            tasks = JVM_AND_COMMON_PUBLISH_TASK
            gradleParams = "$EXCLUDE_DOKA_GENERATION " +
                "-PreleaseVersion=%build.number% " +
                // Dependencies for native source sets are declared in build scripts even if native targets are disabled
                "-Pktorbuild.ignoreExtraSourceSets=true " +
                "-Dmaven.repo.local=$MAVEN_LOCAL_PATH"
            jdkHome = Env.JDK_LTS
        }
    }

    requirements {
        agent(Agents.OS.Linux)
    }

    params {
        param("system.teamcity.default.properties", "teamcity.default.properties")
        text(
            name = "ktor.branch",
            value = "",
            description = "Ktor branch to build. Empty uses the build's default branch",
            allowEmpty = true,
        )
    }

    defaultBuildFeatures(VCSCore.id)
})


fun Dependencies.benchmarksSnapshotBuildDependency() {
    dependency(BuildKtorForBenchmarks) {
        snapshot {
            reuseBuilds = ReuseBuilds.SUCCESSFUL
            onDependencyFailure = FailureAction.FAIL_TO_START
            onDependencyCancel = FailureAction.CANCEL
        }
        artifacts {
            buildRule = sameChainOrLastFinished()
            artifactRules = "ktor-repository.zip!** => ktor-repository"
            cleanDestination = true
        }
    }
}

