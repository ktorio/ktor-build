package subprojects.eap

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*

object SyncPublicJetbrainsSpaceToJetbrainsTeam : BuildType({
    name = "Sync repo: public.jetbrains.space -> jetbrains.team"
    description = "Uploads all artifacts from source repository to target"

    params {
        text("space-url", "https://public.jetbrains.space", display = ParameterDisplay.PROMPT, allowEmpty = true)
        password("space-password", "%space.public.packages.secret%", display = ParameterDisplay.PROMPT)
        text("from-repo-name", "eap", description = "Name of the source repository that will be used", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("from-project-key", "ktor", description = "Name of the source project that will be used. Expected https://public.jetbrains.space/p/ktor/packages but you can use other if you have right permissions.", display = ParameterDisplay.PROMPT, allowEmpty = true)

        param("to-space-url", "https://jetbrains.team")
        param("to-space-password", "%space.packages.secret%")
        text("to-repo-name", "eap", description = "Name of the target repository that will be used", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("to-project-key", "ktor", description = "Name of the target project that will be used. Expected https://jetbrains.team/p/ktor/packages, but you can use other if you have right permissions.", display = ParameterDisplay.PROMPT, allowEmpty = true)

        text("versions", "all", display = ParameterDisplay.PROMPT)
    }

    steps {
        script {
            name = "Execute"
            scriptContent = bashScript("""
                space-cli \
                  sync \
                  --space-url=%space-url% \
                  --space-password=%space-password% \
                  --to-space-url=%to-space-url% \
                  --to-space-password=%to-space-password% \
                  --from-project-key=%from-project-key% \
                  --from-repo-name=%from-repo-name% \
                  --to-project-key=%to-project-key% \
                  --to-repo-name=%to-repo-name% \
                  --versions=%versions%
            """)
            dockerImage = "registry.jetbrains.team/p/ktor/containers/space-cli:latest"
            dockerRunParameters = "-v %teamcity.build.checkoutDir%/.space-sync-cache:/app/.space-sync-cache"
        }
        script {
            name = "Print build status"
            id = "Print_build_status"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """echo "##teamcity[buildStatus text='Sync from %space-url% %from-repo-name% to %to-space-url% %to-repo-name%']""""
        }
    }

    features {
        dockerRegistryConnections {
            cleanupPushedImages = true
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_8"
            }
        }
    }

    artifactRules = "+:.space-sync-cache/** => space-sync-cache.zip"
    publishArtifacts = PublishMode.ALWAYS

    dependencies {
        artifacts(SyncPublicJetbrainsSpaceToJetbrainsTeam.id!!) {
            buildRule = lastFinished()
            artifactRules = "?:space-sync-cache.zip!** => .space-sync-cache"
        }
    }

    triggers {
        retryBuild {
            moveToTheQueueTop = true
            retryWithTheSameRevisions = false
            attempts = Int.MAX_VALUE
            branchFilter = BranchFilter.AllBranches
        }
    }

    requirements {
        agent(Agents.OS.Linux, Agents.Arch.Arm64)
    }

    cleanup {
        baseRule {
            history(days = 30)
        }
    }
})
