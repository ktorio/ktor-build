package subprojects.build.core

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

    steps {
        gradle {
            name = "Run ktlint"
            tasks = "lintKotlin"
            gradleParams = "-PenableCodeStyle=true"
            jdkHome = "%env.${java11.env}%"
            buildFile = "build.gradle.kts"
        }
    }

    triggers {
        vcs {
            // we only verify *.kt, project and plugin configs
            triggerRules = """
                +:**/*.kt
                +:gradle/codestyle.gradle*
                +:build.gradle*
                +:**/*.gradle*
                +:gradle.properties
                +:settings.gradle
                +:.editorconfig
            """.trimIndent()
        }
    }

    failureConditions {
        failOnMetricChange {
            metric = BuildFailureOnMetric.MetricType.INSPECTION_ERROR_COUNT
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.MORE
            compareTo = value()
        }
    }

    features {
        githubCommitStatusPublisher()
        feature {
            type = "xml-report-plugin"
            param("xmlReportParsing.reportType", "checkstyle")
            param("xmlReportParsing.reportDirs", "+:**/build/reports/ktlint/*.xml")
        }
    }
})
