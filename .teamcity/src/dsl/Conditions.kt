package dsl

import jetbrains.buildServer.configs.kotlin.*

fun BuildStepConditions.isWindows() {
    equals("teamcity.agent.os.family", "Windows")
}

fun BuildStepConditions.isNotWindows() {
    doesNotEqual("teamcity.agent.os.family", "Windows")
}
