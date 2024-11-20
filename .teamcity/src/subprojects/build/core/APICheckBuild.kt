package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.*

object APICheckBuild : BuildType({
    id("KtorMatrixCore_APICheck".toId())
    name = "Check API"
    artifactRules = formatArtifacts(memoryReportArtifact)

    vcs {
        root(VCSCore)
    }

    triggers {
        onChangeDefaultOrPullRequest(additionalTriggerRules = TriggerRules.IgnoreBotCommits)
    }

    steps {
        gradle {
            name = "API Check"
            tasks = "apiCheck"
            jdkHome = "%env.${javaLTS.env}%"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        agent(linux)
    }
})
