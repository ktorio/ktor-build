package subprojects.release.apidocs

import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSAPIDocs
import subprojects.VCSToken
import subprojects.VCSUsername
import subprojects.build.apidocs.BuildDokka
import subprojects.build.apidocs.VersionFilename
import subprojects.build.core.formatArtifactsString
import subprojects.build.core.require

object ProjectReleaseAPIDocs : Project({
    id("ProjectKtorReleaseAPIDocs")
    name = "API Docs"

    vcsRoot(VCSAPIDocs)

    params {
        param("env.GITHUB_USER", VCSUsername)
        password("env.GITHUB_PASSWORD", VCSToken)
    }

    buildType {
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
                    KTOR_VERSION="$(cat $VersionFilename)"
    
                    ./build_doc.sh "${'$'}{KTOR_VERSION}" apidoc
    
                    git config user.email "deploy@jetbrains.com"
                    git config user.name "Auto deploy"
                    git remote set-url origin "https://${'$'}{GITHUB_USER}:${'$'}{GITHUB_PASSWORD}@github.com/ktorio/api.ktor.io.git"
                    git add "${'$'}{KTOR_VERSION}" assets/versions.js sitemap.xml latest
                    git commit -m "Update for ${'$'}{KTOR_VERSION}"
                    git push origin master
                """.trimIndent()
            }
        }

        dependencies {
            dependency(BuildDokka) {
                snapshot {
                }

                artifacts {
                    artifactRules = formatArtifactsString("apidoc.zip!**=>apidoc", VersionFilename)
                }
            }
        }

        requirements {
            require(os = "Linux")
        }
    }
})
