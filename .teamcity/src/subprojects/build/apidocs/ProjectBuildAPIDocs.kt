package subprojects.build.apidocs

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSCore
import subprojects.build.core.formatArtifactsString
import subprojects.build.defaultTimeouts

const val VersionFilename = "ktor_version.txt"

object ProjectBuildAPIDocs : Project({
    id("ProjectKtorBuildAPIDocs")
    name = "API Docs"

    params {
        defaultTimeouts()
    }

    buildType(BuildDokka)
})

object BuildDokka: BuildType({
    id("KtorAPIDocs_Dokka")
    name = "Build Dokka artifacts"

    vcs {
        root(VCSCore)
    }

    requirements {
        noLessThan("teamcity.agent.hardware.memorySizeMb", "7000")
        contains("teamcity.agent.jvm.os.name", "Linux")
    }

    artifactRules = formatArtifactsString("+:apidoc => apidoc.zip", VersionFilename)

    steps {
        gradle {
            name = "Run Dokka"
            tasks = "dokkaWebsite"
        }
        script {
            name = "Get version number"
            scriptContent = "./gradlew properties --no-daemon --console=plain -q | sed -n 's/^version: //p' > $VersionFilename"
        }
    }
})
