package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSAPIDocs
import subprojects.VCSCore

object ProjectAPIDocs : Project({
    id("ProjectKtorAPIDocs")
    name = "API Docs"

    params {
        param("system.org.gradle.internal.http.connectionTimeout", "120000")
        param("system.org.gradle.internal.http.socketTimeout", "120000")
    }

    vcsRoot(VCSAPIDocs)

    val dokka = buildType {
        id("KtorAPIDocs_Dokka")
        name = "Build Dokka artifacts"

        vcs {
            root(VCSCore)
        }

        requirements {
            noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
            contains("teamcity.agent.jvm.os.name", "Linux")
        }

        artifactRules = """
            +:apidoc => apidoc.tgz
            +:ktor_version.txt => ktor_version.txt
        """.trimIndent()

        steps {
            gradle {
                name = "Run Dokka"
                tasks = "dokkaWebsite"
            }
            script {
                name = "Get version number"
                scriptContent = "./gradlew properties --no-daemon --console=plain -q | sed -n 's/^version: //p' > ktor_version.txt"
            }
        }
    }

    buildType {
        id("KtorAPIDocs_Deploy")
        name = "Deploy website"

        vcs {
            root(VCSAPIDocs)
        }

        dependencies {
            dependency(dokka) {
                snapshot {
                }

                artifacts {
                    artifactRules = """
                        apidoc.tgz=>apidoc
                        ktor_version.txt=>ktor_version.txt
                    """.trimIndent()
                }
            }
        }

        steps {
            script {
                name = "Create pull request on Github"
                scriptContent = """
                    ls -la
                    ls -la apidoc
                    cat ktor_version.txt
                """.trimIndent()
            }
        }
    }
})