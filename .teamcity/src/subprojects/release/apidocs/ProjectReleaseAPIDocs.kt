package subprojects.release.apidocs

import dsl.*
import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.build.core.*
import subprojects.release.*

object ProjectReleaseAPIDocs : Project({
    id("ProjectKtorReleaseAPIDocs")
    name = "API Docs"

    vcsRoot(VCSAPIDocs)

    params {
        password("env.GITHUB_TOKEN", "n/a")

        configureReleaseVersion()
    }

    apiBuild = buildType {
        id("KtorAPIDocs_Deploy")
        name = "Deploy API docs website"

        vcs {
            root(VCSAPIDocs)
            checkoutMode = CheckoutMode.ON_AGENT
        }

        params {
            param("versionsDirectory", "%teamcity.build.checkoutDir%/versions")
        }

        consumeGradleDependencyCache()

        steps {
            script {
                name = "Prepare API docs build"
                scriptContent = "./build_doc.sh \"%releaseVersion%\" prepare \"%versionsDirectory%\""
            }

            installCocoapods()

            gradle {
                name = "Generate API docs"
                tasks = ":ktor-dokka:dokkaGenerate"
                gradleParams = "-Pversion=%releaseVersion% " +
                    "-Pktor.dokka.versionsDirectory=%versionsDirectory% " +
                    "-Porg.gradle.internal.network.retry.max.attempts=10 " +
                    "--no-configuration-cache --info"
                workingDir = "ktor"
                jdkHome = Env.JDK_LTS
            }

            script {
                name = "Finalize API docs build and push changes to git"
                scriptContent = bashScript("""
                    ./build_doc.sh "%releaseVersion%" finalize "%versionsDirectory%"
                    git config user.name "TeamCity"
                    git config user.email "teamcity@jetbrains.com"
                    git remote set-url origin "https://oauth2:${'$'}{GITHUB_TOKEN}@github.com/ktorio/api.ktor.io.git"
                    git add docs/
                    git commit --message "Update for %releaseVersion%"
                    git push origin main
                """)
            }
        }

        requirements {
            agent(Agents.OS.MacOS, Agents.Arch.Arm64)
        }

        features {
            perfmon {}

            gitHubAppBuildScopedToken {
                parameterName = "env.GITHUB_TOKEN"
                connectionId = "PROJECT_EXT_7"
                targetRepositories = "api.ktor.io"
            }
        }
    }
})
