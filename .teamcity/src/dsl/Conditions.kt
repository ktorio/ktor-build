package dsl

import jetbrains.buildServer.configs.kotlin.*

// Every agent reports the JVM parameter. Only the agents of some servers report `teamcity.agent.os.family`.
private const val JVM_OS_NAME = "teamcity.agent.jvm.os.name"

fun BuildStepConditions.isWindows() {
    contains(JVM_OS_NAME, "Windows")
}

fun BuildStepConditions.isNotWindows() {
    doesNotContain(JVM_OS_NAME, "Windows")
}
