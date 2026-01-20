package subprojects.release.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*

object ProjectReleaseGeneratorWebsite : Project({
    id("ProjectKtorReleaseGeneratorWebsite")
    name = "Generator Website"

    params {
        password("env.PUBLISHING_TOKEN", value = "%space.packages.publish.token%")
    }

    buildType {
        id("KtorGeneratorWebsite_Deploy")
        name = "Deploy Generator Website"

        vcs {
            root(VCSKtorGeneratorWebsite, "+:.=>ktor-generator-website")
            checkoutMode = CheckoutMode.ON_AGENT
        }

        steps {
            nodeJS {
                name = "Build website"
                workingDir = "ktor-generator-website"
                shellScript = """
                    set -e
                    npm install --verbose
                    npm run build --verbose
                """.trimIndent()
            }
            script {
                name = "Upload archive to Space"
                workingDir = "ktor-generator-website"
                scriptContent = """
                   tar -zcvf website.tar.gz -C build .
                   curl -i \
                      -H "Authorization: Bearer %env.PUBLISHING_TOKEN%" \
                      https://packages.jetbrains.team/files/p/ktor/files/ktor-generator-website/main/ \
                      --upload-file website.tar.gz
                """.trimIndent()
            }
        }

        triggers {
            vcs {
                branchFilter = BranchFilter.DefaultBranch
            }
        }
    }
})
