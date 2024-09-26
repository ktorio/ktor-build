package subprojects.release.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*

object ProjectReleaseGeneratorWebsite : Project({
    id("ProjectKtorReleaseGeneratorWebsite")
    name = "Generator Website"

    params {
        param("env.GITHUB_FORK", "ktorio")
        param("env.GITHUB_USER", VCSUsername)
        password("env.GITHUB_PASSWORD", VCSToken)

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
            script {
                name = "Commit to github pages repository"
                scriptContent = """
                    set -e
                    git clone "https://${'$'}{GITHUB_USER}:${'$'}{GITHUB_PASSWORD}@github.com/${'$'}{GITHUB_FORK}/ktor-init-tools.git"
                    cd ktor-init-tools 
                    git checkout generator
                    rm * -rf
                    cp -R ../ktor-generator-website/build/* ./
                    echo start.ktor.io > CNAME
                    git config user.email "ktor@jetbrains.com"
                    git config user.name "ktor"
                    git add .
                    git commit -m "Deploy website"
                    git push origin generator
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
