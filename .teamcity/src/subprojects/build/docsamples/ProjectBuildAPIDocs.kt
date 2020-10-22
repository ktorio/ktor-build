package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSCore
import subprojects.build.core.addArtifacts

const val versionFilename = "ktor_version.txt"

object ProjectBuildAPIDocs : Project({
    id("ProjectKtorBuildAPIDocs")
    name = "API Docs"

    params {
        param("system.org.gradle.internal.http.connectionTimeout", "120000")
        param("system.org.gradle.internal.http.socketTimeout", "120000")
    }

    buildType {
        id("KtorAPIDocs_Dokka")
        name = "Build Dokka artifacts"

        vcs {
            root(VCSCore)
        }

        requirements {
            noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
            contains("teamcity.agent.jvm.os.name", "Linux")
        }

        artifactRules = addArtifacts("+:apidoc => apidoc.zip", versionFilename)

        steps {
            gradle {
                name = "Run Dokka"
                tasks = "dokkaWebsite"
            }
            script {
                name = "Get version number"
                scriptContent = "./gradlew properties --no-daemon --console=plain -q | sed -n 's/^version: //p' > $versionFilename"
            }
        }
    }
})
