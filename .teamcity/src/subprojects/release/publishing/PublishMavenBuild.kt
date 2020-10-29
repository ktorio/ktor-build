package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.core.*
import java.io.*

class PublishMavenBuild(private val publishingData: PublishingData) : BuildType({
    id("KtorPublishMavenBuild_${publishingData.buildName}".toExtId())
    name = "Publish ${publishingData.buildName} to Maven"
    vcs {
        root(VCSCore)
    }
    steps {
        val gpgDir = "%env.SIGN_KEY_LOCATION%"
        prepareKeyFile(gpgDir)

        gradle {
            name = "Parallel assemble"
            tasks = publishingData.gradleTasks.joinToString(" ")
        }
        cleanupKeyFile(gpgDir)
    }
    dependencies {
        val buildId = publishingData.buildData.id
        artifacts(buildId) {
            buildRule = lastSuccessful()
            artifactRules = formatArtifacts(publishingData.buildData.artifacts, consumedGradleCacheArtifact, consumedBuildArtifact)
        }
    }
    requirements {
        require(publishingData.operatingSystem)
    }
})

fun BuildSteps.prepareKeyFile(workingDirectory: String) {
    script {
        name = "Prepare gnupg"
        scriptContent = """
                            cd $workingDirectory
                            export HOME=${'$'}(pwd)
                            export GPG_TTY=${'$'}(tty)
                            
                            rm -rf .gnupg
                            
                            cat >keyfile <<EOT
                            %env.SIGN_KEY_PRIVATE%
                            EOT
                            gpg --allow-secret-key-import --batch --import keyfile
                            rm -v keyfile
                            
                            cat >keyfile <<EOT
                            %env.SIGN_KEY_PUBLIC%
                            EOT
                            gpg --batch --import keyfile
                            rm -v keyfile
                        """
        workingDir = "."
    }
}

fun BuildSteps.cleanupKeyFile(workingDirectory: String = ".") {
    script {
        name = "Cleanup"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
                            cd $workingDirectory
                            rm -rf .gnupg
                        """
        workingDir = "."
    }
}

