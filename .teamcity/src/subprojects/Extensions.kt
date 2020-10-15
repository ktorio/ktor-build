package subprojects

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*

fun BuildFeatures.setupPerformanceMonitoring() {
    perfmon {
    }
}

fun Triggers.setupDefaultVcsTrigger() {
    vcs {
        branchFilter = "+:*"
        quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_DEFAULT
    }
}