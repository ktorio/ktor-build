package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.core.*

data class Build(val name: String, val build: BuildType?)

object ProjectPublishing : Project({
    id("ProjectPublishing")
    name = "Publishing"
    description = "Publish artifacts to repositories"

    val publishingTargets = listOf(
        Build("JVM", generatedBuilds["${linux.name}${java11.name}"]),
        Build("JavaScript", generatedBuilds[js.name]),
        Build("Windows", generatedBuilds[windows.name]),
        Build("Linux", generatedBuilds[linux.name]),
        Build("macOS", generatedBuilds[macOS.name]))

    val allBuilds = publishingTargets.map(::PublishMavenBuild)

    allBuilds.forEach(::buildType)

    buildType {
        createCompositeBuild("KtorPublish_All", "Publish All", VCSCore, allBuilds)
    }
})
