package subprojects.release.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.nodeJS
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import subprojects.VCSKtorGeneratorWebsite
import subprojects.VCSToken
import subprojects.VCSUsername
import subprojects.build.*
import subprojects.build.core.*

object ProjectReleaseGeneratorWebsite : Project({
    id("ProjectKtorReleaseGeneratorWebsite")
    name = "Generator Website"

    params {
        param("env.GITHUB_FORK", "ktorio")
        param("env.GITHUB_USER", VCSUsername)
        password("env.GITHUB_PASSWORD", VCSToken)
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
                name = "Build website and commit to repo"
                shellScript = """
                    set -e
                    cd ktor-generator-website
                    npm install --verbose
                    npm run build --verbose
                    cd ..
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
                branchFilter = "+:<default>"
            }
        }
    }
})