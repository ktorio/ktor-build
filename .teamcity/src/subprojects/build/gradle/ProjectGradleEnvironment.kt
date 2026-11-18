package subprojects.build.gradle

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.DAY.*
import subprojects.*
import subprojects.build.*
import kotlin.time.Duration.Companion.minutes

object ProjectGradleEnvironment : Project({
    id("ProjectGradleEnvironment")
    name = "Ktor Gradle Caches"
    description = "Build reusable Gradle caches for Ktor"

    params {
        defaultGradleParams()
    }

    Core.gradleEnvironments.forEach(::buildType)

    buildType {
        id("GradleEnvironment")
        name = "Build Gradle Environment"
        description = "Build Gradle environments for all platforms"
        type = Type.COMPOSITE

        vcs {
            root(VCSCore)
        }

        dependencies {
            for (environment in Core.gradleEnvironments) {
                snapshot(environment) {}
            }
        }

        // Set here rather than in each environment, so all environments are built from the same revision
        triggers {
            vcs {
                branchFilter = preparedBranches.joinToString("\n") { "+:$it" }
                // Changes in settings and included builds invalidate the instrumented build logic classpath cached in the user home
                triggerRules = """
                    +:root=${VCSCore.id}:gradle/libs.versions.toml
                    +:root=${VCSCore.id}:gradle/wrapper/gradle-wrapper.properties
                    +:root=${VCSCore.id}:gradle/gradle-daemon-jvm.properties
                    +:root=${VCSCore.id}:settings.gradle.kts
                    +:root=${VCSCore.id}:build-logic/**
                    +:root=${VCSCore.id}:build-settings-logic/**
                    +:root=${VCSCore.id}:ktor-test-server/**
                """.trimIndent()

                quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
                quietPeriod = 10.minutes.inWholeSeconds.toInt()
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
    }
})
