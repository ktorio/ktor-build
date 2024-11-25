package dsl

import jetbrains.buildServer.configs.kotlin.*

fun BuildStepConditions.isWindows() {
    equals("teamcity.agent.jvm.os.family", "Windows")
}

fun BuildStepConditions.isNotWindows() {
    doesNotEqual("teamcity.agent.jvm.os.family", "Windows")
}
