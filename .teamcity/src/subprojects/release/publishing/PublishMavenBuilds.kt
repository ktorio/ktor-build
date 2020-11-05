package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

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
            ), gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
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
            ), gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
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
        powerShell {
            name = "Get dependencies and environment ready"
            scriptMode = script {
                content = """
                choco install -y gnupg
                $libcurlSoftware
                Stop-Process -Name "gpg-agent" -ErrorAction SilentlyContinue
                echo ##teamcity[setParameter name='env.PATH' value='%env.PATH%;C:\Program Files (x86)\Gpg4win\..\GnuPG\bin\']
            """.trimIndent()
            }
        }
        publishToMaven(
            listOf(
                "publishMingwX64PublicationToMavenRepository"
            ),
            gradleParams = "-P\"signing.gnupg.executable=gpg.exe\" -P\"signing.gnupg.homeDir=C:\\Users\\buildUser\\AppData\\Roaming\\gnupg\\\"",
            os = "Windows"
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
            ), gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
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
            ), gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
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

fun BuildSteps.prepareKeyFile(os: String = "") {
    when (os) {
        "Windows" -> {
            powerShell {
                name = "Prepare Keys"
                scriptMode = script {
                    content = """
md ${'$'}Env:SIGN_KEY_LOCATION -force
cd ${'$'}Env:SIGN_KEY_LOCATION
echo "Removing GNUPG folder"
rm -r -fo C:\Users\builduser\AppData\Roaming\gnupg\

# Hard-coding path for GPG since this fails on TeamCity
# ${'$'}gpg=(get-command gpg.exe).Path
${'$'}gpg="C:\Program Files (x86)\Gpg4win\..\GnuPG\bin\gpg.exe"
Set-Alias -Name gpg2.exe -Value ${'$'}gpg

echo "Exporting public key"
[System.IO.File]::WriteAllText("${'$'}pwd\keyfile", ${'$'}Env:SIGN_KEY_PUBLIC)
& ${'$'}gpg --batch --import keyfile
rm keyfile


echo "Exporting private key"

[System.IO.File]::WriteAllText("${'$'}pwd\keyfile", ${'$'}Env:SIGN_KEY_PRIVATE)
& ${'$'}gpg --allow-secret-key-import --batch --import keyfile
rm keyfile
            """.trimIndent()
                }
            }
        }
        else -> {
            script {
                name = "Prepare Keys"
                scriptContent = """
#!/bin/sh
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
                workingDir = "."
            }
        }
    }
}

fun BuildSteps.cleanupKeyFile(os: String = "") {
    when (os) {
        "Windows" -> {
            powerShell {
                name = "Cleanup Keys"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                scriptMode = script {
                    content = """
rm -r -fo C:\Users\builduser\AppData\Roaming\gnupg\
            """.trimIndent()
                }
            }
        }
        else -> {
            script {
                name = "Cleanup Keys"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                scriptContent = """
cd .
rm -rf .gnupg
                        """.trimIndent()
                workingDir = "."
            }
        }
    }
}

private fun BuildSteps.publishToMaven(gradleTasks: List<String>, gradleParams: String = "", os: String = "") {
    prepareKeyFile(os)
    gradle {
        name = "Assemble"
        tasks =
            "${gradleTasks.joinToString(" ")} --i -PreleaseVersion=%releaseVersion% $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${java11.env}%"
    }
    cleanupKeyFile(os)
}


