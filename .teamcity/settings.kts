import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
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

    subProject(Build)
    subProject(Release)
}


object Build : Project({
    name = "Build"
    description = "Build configurations that build Ktor"

    vcsRoot(Build_Ktor)

    subProject(Build_Generator)
    subProject(Build_Samples)
    subProject(Build_Core)
    subProject(Build_DocSamples)
    subProject(Build_Plugin)
})

object Build_Core : Project({
    name = "Core"
    description = "Ktor Core Framework"

    buildType(Build_Core_Compile)
})

object Build_Core_Compile : BuildType({
    name = "Compile"

    vcs {
        root(Build_Ktor)
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
            gradleWrapperPath = ""
        }
    }

    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})


object Build_DocSamples : Project({
    name = "Doc Code"
    description = "Code samples included in Docs"
})


object Build_Generator : Project({
    name = "Generator"
    description = "Code for start.ktor.io"
})


object Build_Plugin : Project({
    name = "Plugin"
    description = "Code for IntelliJ IDEA plugin"
})


object Build_Samples : Project({
    name = "Samples"
    description = "Code samples"
})


object Release : Project({
    name = "Release"
    description = "Build configuration that release Ktor"
})
