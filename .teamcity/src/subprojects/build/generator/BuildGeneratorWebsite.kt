package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

object BuildGeneratorWebsite : BuildType({
    id("KtorGeneratorWebsite_Test")
    name = "Build generator website"
    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
    }

    vcs {
        root(VCSKtorGeneratorWebsite)
    }

    steps {
        nodeJS {
            name = "Node.js test + build"
            shellScript = """
                npm ci
                npm run lint
                npm run build --verbose
            """.trimIndent()
        }
        script {
            name = "Upload test archive to Space"
            scriptContent = """
               tar -zcvf website.tar.gz -C build .
               curl -i \
                  -H "Authorization: Bearer %env.PUBLISHING_TOKEN%" \
                  https://packages.jetbrains.team/files/p/ktor/files/ktor-generator-website/test/ \
                  --upload-file website.tar.gz
            """.trimIndent()
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

    triggers {
        vcs {
            branchFilter = BranchFilter.PullRequest
        }
    }
})
