package ktor

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version
import ktor.subprojects.build.core.*
import ktor.subprojects.build.docsamples.*
import ktor.subprojects.build.generator.*
import ktor.subprojects.build.plugin.*
import ktor.subprojects.build.samples.*
import ktor.subprojects.release.*

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and ktor.subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2020.1"

project {
    description = "Ktor Framework"

    subProject(ProjectBuild)
    subProject(ProjectRelease)
}



object ProjectBuild : Project({

    description = "Build configurations that build Ktor"

    vcsRoot(VCSCore)
    vcsRoot(VCSSamples)

    subProject(ProjectGenerator)
    subProject(ProjectSamples)
    subProject(ProjectCore)
    subProject(ProjectDocSamples)
    subProject(ProjectPlugin)
})

