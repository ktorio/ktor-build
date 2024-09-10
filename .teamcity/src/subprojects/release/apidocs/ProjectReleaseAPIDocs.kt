package subprojects.release.apidocs

import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSAPIDocs
import subprojects.VCSToken
import subprojects.VCSUsername
import subprojects.build.core.*
import subprojects.build.macOS
import subprojects.release.*

object ProjectReleaseAPIDocs : Project({
    id("ProjectKtorReleaseAPIDocs")
    name = "API Docs"

    params {
        param("env.GITHUB_USER", VCSUsername)
        password("env.GITHUB_PASSWORD", VCSToken)

        configureReleaseVersion()
    }

    apiBuild = buildType {
        id("KtorAPIDocs_Deploy")
        name = "Deploy API docs website"

        vcs {
            root(VCSAPIDocs)
            checkoutMode = CheckoutMode.ON_AGENT
        }

        steps {
            script {
                name = "Generate static files for version and push changes to git"
                scriptContent = """
                    ./build_doc.sh "%releaseVersion%"
                    git config user.email "deploy@jetbrains.com"
                    git config user.name "Auto deploy"
                    git remote set-url origin "https://${'$'}{GITHUB_USER}:${'$'}{GITHUB_PASSWORD}@github.com/ktorio/api.ktor.io.git"
                    git add .
                    git commit -m "Update for %releaseVersion%"
                    git push origin main
                """.trimIndent()
            }
        }

        requirements {
            require(os = macOS.agentString, minMemoryMB = 12000)
        }
    }
})
