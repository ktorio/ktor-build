package subprojects

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.*

fun BuildFeatures.setupPerformanceMonitoring() {
    perfmon {
    }
}