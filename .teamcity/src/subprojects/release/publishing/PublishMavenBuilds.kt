package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*
import java.io.*

object PublishJvmToMaven : BuildType({
    createDeploymentBuild("KtorPublishJvmToMavenBuild", "Publish JVM to Maven")
    vcs {
        root(VCSCore)
    }
    steps {
        publishToMaven(
            listOf(
                "publishJvmPublicationToMavenRepository",
                "publishKotlinMultiplatformPublicationToMavenRepository"
            )
        )
    }
    dependencies {
        snapshot(jvmBuild!!) {
        }
    }
    requirements {
        require(linux.agentString)
    }
})

object PublishJSToMaven : BuildType({
    createDeploymentBuild("KtorPublishJSToMavenBuild", "Publish JS to Maven")
    vcs {
        root(VCSCore)
    }
    steps {
        publishToMaven(
            listOf(
                "publishJsPublicationToMavenRepository"
            )
        )
    }
    dependencies {
        snapshot(jsBuild!!) {
        }
    }
    requirements {
        require(linux.agentString)
    }
})

object PublishWindowsNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishWindowsNativeToMavenBuild", "Publish Windows Native to Maven")
    vcs {
        root(VCSCore)
    }
    steps {
        script {
            name = "Install Cgywin"
            scriptContent = """
                choco install cygwin -y
            """.trimIndent()
        }
        publishToMaven(
            listOf(
                "publishMingwX64PublicationToMavenRepository"
            ), executionMode = ExecutionMode.FILE
        )
    }
    dependencies {
        snapshot(nativeWindowsBuild!!) {
        }
    }
    requirements {
        require(windows.agentString)
    }
})

object PublishLinuxNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishLinuxNativeToMavenBuild", "Publish Linux Native to Maven")
    vcs {
        root(VCSCore)
    }
    steps {
        publishToMaven(
            listOf(
                "publishLinuxX64PublicationToMavenRepository"
            )
        )
    }
    dependencies {
        snapshot(nativeLinuxBuild!!) {
        }
        snapshot(PublishWindowsNativeToMaven) {
        }
    }
    requirements {
        require(linux.agentString)
    }
})

object PublishMacOSNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishMacOSNativeToMavenBuild", "Publish Mac Native to Maven")
    vcs {
        root(VCSCore)
    }
    steps {
        publishToMaven(
            listOf(
                "publishIosArm32PublicationToMavenRepository",
                "publishIosArm64PublicationToMavenRepository",
                "publishIosX64PublicationToMavenRepository",
                "publishMacosX64PublicationToMavenRepository",
                "publishTvosArm64PublicationToMavenRepository",
                "publishTvosX64PublicationToMavenRepository",
                "publishWatchosArm32PublicationToMavenRepository",
                "publishWatchosArm64PublicationToMavenRepository",
                "publishWatchosX86PublicationToMavenRepository",
                "publishMetadataPublicationToMavenRepository"
            )
        )
    }
    dependencies {
        snapshot(nativeMacOSBuild!!) {
        }
        snapshot(PublishWindowsNativeToMaven) {
        }
    }
    requirements {
        require(macOS.agentString)
    }
})

enum class ExecutionMode {
    DIRECT,
    FILE
}

fun BuildSteps.prepareKeyFile(executionMode: ExecutionMode = ExecutionMode.DIRECT) {
    val script = """#!/bin/sh
set -eux pipefail
mkdir -p %env.SIGN_KEY_LOCATION%
cd "%env.SIGN_KEY_LOCATION%"
export HOME=${'$'}(pwd)
export GPG_TTY=${'$'}(tty)
rm -rf .gnupg
echo "Exporting public key"
cat >keyfile <<EOT
%env.SIGN_KEY_PUBLIC%
EOT
gpg --batch --import keyfile
rm -v keyfile
echo "Exporting private key"
cat >keyfile <<EOT
%env.SIGN_KEY_PRIVATE%
EOT
gpg --allow-secret-key-import --batch --import keyfile
rm -v keyfile
""".trimIndent()
    when (executionMode) {
        ExecutionMode.DIRECT -> {
            script {
                name = "Prepare gnupg"
                scriptContent = script
                workingDir = "."
            }
        }
        ExecutionMode.FILE -> {
            val filename = "%env.SIGN_KEY_LOCATION%/prepkey.sh"
            val file = File(filename)
            file.writeText(script)
            script {
                name = "Prepare gnupg"
                scriptContent = """
                    C:\Tools\Cygwin\bin\bash ${file.absoluteFile}
                """.trimIndent()
                workingDir = "%env.SIGN_KEY_LOCATION%"
            }
        }
    }
}

fun BuildSteps.cleanupKeyFile() {
    script {
        name = "Cleanup"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = """
cd .
rm -rf .gnupg
                        """.trimIndent()
        workingDir = "."
    }
}

private fun BuildSteps.publishToMaven(gradleTasks: List<String>, executionMode: ExecutionMode = ExecutionMode.DIRECT) {
    prepareKeyFile(executionMode)
    gradle {
        name = "Parallel assemble"
        tasks = gradleTasks.joinToString(" ") + " --i -PreleaseVersion=%releaseVersion%"
    }
    cleanupKeyFile()
}


