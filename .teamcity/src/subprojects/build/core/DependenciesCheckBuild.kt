package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.toExtId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import subprojects.VCSCore
import subprojects.build.githubCommitStatusPublisher
import subprojects.build.githubPullRequestsLoader
import subprojects.build.java11
import subprojects.build.linux

class DependenciesCheckBuild : BuildType({
    id("KtorDependenciesCheckBuildId".toExtId())
    name = "Ktor Dependencies Check"

    vcs {
        root(VCSCore)
    }

    steps {
        gradle {
            name = "Check Dependencies"
            buildFile = "build.gradle.kts"
            tasks = "snyk-test"
            jdkHome = "%env.${java11.env}%"
        }
    }

    features {
        perfmon {}
        githubPullRequestsLoader(VCSCore.id.toString())
        githubCommitStatusPublisher(VCSCore.id.toString())
    }

    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})