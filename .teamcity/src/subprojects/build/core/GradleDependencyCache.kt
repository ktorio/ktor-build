package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.*
import subprojects.*
import kotlin.time.Duration.Companion.hours

private const val cacheArtifactName = "gradle-dependency-cache.zip"
private const val cacheDirectoryName = ".gradle-dependency-cache"
private const val cacheDirectory = "%teamcity.build.checkoutDir%/$cacheDirectoryName"

object SeedGradleDependencyCache : BuildType({
    id("SeedGradleDependencyCache")
    name = "Seed Gradle dependency cache"

    vcs {
        root(VCSCore)
        checkoutMode = CheckoutMode.ON_AGENT
    }

    params {
        param("env.GRADLE_USER_HOME", "%teamcity.build.checkoutDir%/.gradle-user-home")
        param("env.GRADLE_RO_DEP_CACHE", cacheDirectory)
    }

    steps {
        gradle {
            name = "Resolve dependencies"
            tasks = "help"
            gradleParams = "--write-verification-metadata sha256 --dry-run --info --no-configuration-cache"
            jdkHome = Env.JDK_LTS
        }

        script {
            name = "Prepare dependency cache for publishing"
            scriptContent = bashScript("""
                source="${'$'}GRADLE_USER_HOME/caches/modules-2"
                destination="$cacheDirectory/modules-2"

                if [[ -d "${'$'}source" ]]; then
                    mkdir -p "${'$'}destination"
                    rsync -a \
                        --exclude='*.lock' \
                        --exclude='gc.properties' \
                        "${'$'}source/" \
                        "${'$'}destination/"
                fi
            """)
        }
    }

    artifactRules = "+:$cacheDirectoryName/** => $cacheArtifactName"
    publishArtifacts = PublishMode.SUCCESSFUL

    dependencies {
        artifacts(SeedGradleDependencyCache.id!!) {
            buildRule = lastSuccessful()
            artifactRules = "?:$cacheArtifactName!** => $cacheDirectoryName"
            cleanDestination = true
        }
    }

    triggers {
        vcs {
            branchFilter = BranchFilter.DefaultBranch
            triggerRules = """
                +:root=${VCSCore.id}:gradle/libs.versions.toml
                +:root=${VCSCore.id}:gradle/wrapper/gradle-wrapper.properties
            """.trimIndent()

            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 1.hours.inWholeSeconds.toInt()
        }

        schedule {
            schedulingPolicy = weekly {
                dayOfWeek = Sunday
                hour = 18
            }
            branchFilter = BranchFilter.DefaultBranch
            withPendingChangesOnly = true
            triggerBuild = always()
        }
    }

    requirements {
        agent(Agents.OS.Linux)
    }

    features {
        perfmon {}
    }

    cleanup {
        baseRule {
            artifacts(builds = 1)
        }
    }
})

fun BuildType.consumeGradleDependencyCache() {
    params {
        param("env.GRADLE_RO_DEP_CACHE", cacheDirectory)
    }

    dependencies {
        artifacts(SeedGradleDependencyCache.id!!) {
            buildRule = lastSuccessful()
            artifactRules = "?:$cacheArtifactName!** => $cacheDirectoryName"
            cleanDestination = true
        }
    }
}
