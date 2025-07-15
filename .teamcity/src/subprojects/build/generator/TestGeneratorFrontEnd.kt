package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

object TestGeneratorFrontEnd : BuildType({
    id("KtorGeneratorFrontendVerify")
    name = "Test generator frontend"
    params {
        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%")
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%")
    }

    vcs {
        root(VCSKtorGeneratorWebsite)
    }

    steps {
        script {
            name = "Trigger GitHub Actions Workflow"
            scriptContent = """
            curl -X POST \
            -H "Authorization: token %github.token%" \
            -H "Accept: application/vnd.github.v3+json" \
            https://api.github.com/repos/ktorio/ktor-generator-website/actions/workflows/playwright-tests.yml/dispatches \
            -d '{
                "ref": "main", 
                "inputs": {
                    "registry_username": "%env.SPACE_USERNAME%", 
                    "registry_password": "%env.SPACE_PASSWORD%"
                }
            }'
        """
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

    triggers {
        vcs {}
    }
})
