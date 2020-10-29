package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.core.*
import kotlin.require

class PublishMavenBuild(private val publishingData: PublishingData) : BuildType({
    id("KtorPublishMavenBuild_${publishingData.buildName}".toExtId())
    name = "Publish ${publishingData.buildName} to Maven"
    vcs {
        root(VCSCore)
    }
    steps {
        val gpgDir = "."
        val workdir = VCSCore.agentGitPath.toString()
        prepareKeyFile(gpgDir, workdir)

        gradle {
            name = "Parallel assemble"
            tasks = publishingData.gradleTasks.joinToString(" ")
        }
        cleanupKeyFile(gpgDir, workdir)
    }
    dependencies {
        val buildId = publishingData.buildData.id
        artifacts(buildId) {
            buildRule = lastSuccessful()
            artifactRules = publishingData.buildData.artifacts
        }
    }
    requirements {
        require(publishingData.operatingSystem)
    }
})

fun BuildSteps.prepareKeyFile(dir: String, scriptWorkDir: String) {
    script {
        name = "Prepare gnupg"
        scriptContent = """
                            cd $dir
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
        workingDir = scriptWorkDir
    }
}

fun BuildSteps.cleanupKeyFile(dir: String = ".", scriptWorkDir: String) {
    script {
        name = "Cleanup"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
                            cd $dir
                            rm -rf .gnupg
                        """
        workingDir = scriptWorkDir
    }
}

