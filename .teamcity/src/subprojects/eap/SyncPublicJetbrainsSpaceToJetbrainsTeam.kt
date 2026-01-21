package subprojects.eap

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*

object SyncPublicJetbrainsSpaceToJetbrainsTeam : BuildType({
    name = "Sync repo: public.jetbrains.space -> jetbrains.team"
    description = "Uploads all artifacts from source repository to target"

    artifactRules = "space => space.zip"

    params {
        text("space-url", "https://public.jetbrains.space", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("space-username", "%space.public.packages.user%", display = ParameterDisplay.PROMPT, allowEmpty = true)
        password("space-password", "%space.public.packages.secret%", display = ParameterDisplay.PROMPT)
        text("from-repo-name", "eap", description = "Name of the source repository that will be used", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("from-project-key", "ktor", description = "Name of the source project that will be used. Expected https://public.jetbrains.space/p/ktor/packages but you can use other if you have right permissions.", display = ParameterDisplay.PROMPT, allowEmpty = true)

        param("to-space-url", "https://jetbrains.team")
        param("to-space-username", "%space.packages.user%")
        param("to-space-password", "%space.packages.secret%")
        text("to-repo-name", "eap", description = "Name of the target repository that will be used", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("to-project-key", "ktor", description = "Name of the target project that will be used. Expected https://jetbrains.team/p/ktor/packages, but you can use other if you have right permissions.", display = ParameterDisplay.PROMPT, allowEmpty = true)
    }

    steps {
        script {
            name = "Execute"
            //language=bash
            scriptContent = """
                publishing-utils \
                 space-sync \
                  --space-url=%space-url% \
                  --space-password=%space-password% \
                  --to-space-url=%to-space-url% \
                  --to-space-username=%to-space-username% \
                  --to-space-password=%to-space-password% \
                  --from-project-key=%from-project-key% \
                  --from-repo-name=%from-repo-name% \
                  --to-project-key=%to-project-key% \
                  --to-repo-name=%to-repo-name% \
                  --versions=all
            """.trimIndent()
            dockerImage = "registry.jetbrains.team/p/kti/containers/publishing-utils:188"
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

    requirements {
        agent(Agents.OS.Linux)
    }

    cleanup {
        baseRule {
            history(days = 30)
        }
    }
})
