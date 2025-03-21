package subprojects.build.core

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.failureConditions.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

object CodeStyleVerify : BuildType({
    id("KtorCodeStyleVerifyKtLint")
    name = "CodeStyle verify"

    artifactRules = "+:**/build/reports/ktlint/*.txt => plain.tgz"

    vcs {
        root(VCSCore)
    }

    cancelPreviousBuilds()
    steps {
        gradle {
            name = "Run ktlint"
            tasks = "lintKotlin"
            gradleParams = "-PenableCodeStyle=true -Dorg.gradle.configuration-cache.inputs.unsafe.ignore.file-system-checks=../../temp/buildTmp/teamcity.build.parameters.static"
            jdkHome = Env.JDK_LTS
        }
    }

    triggers {
        vcs {
            // we only verify *.kt, project and plugin configs
            triggerRules = """
                ${TriggerRules.GradleFiles}
                +:**/*.kt
                +:.editorconfig
            """.trimIndent()
            branchFilter = BranchFilter.DefaultOrPullRequest
        }
    }

    failureConditions {
        failOnMetricChange {
            metric = BuildFailureOnMetric.MetricType.INSPECTION_ERROR_COUNT
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.MORE
            compareTo = value()
            threshold = 0
        }
    }

    features {
        feature {
            type = "xml-report-plugin"
            param("xmlReportParsing.reportType", "checkstyle")
            param("xmlReportParsing.reportDirs", "+:**/build/reports/ktlint/*.xml")
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())
    gradleConfigurationCache()

    requirements {
        agent(Agents.OS.Linux)
    }
})
