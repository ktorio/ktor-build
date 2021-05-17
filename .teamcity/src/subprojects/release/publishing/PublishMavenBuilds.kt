package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.*
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.release.*

const val releaseVersion = "%releaseVersion%"

object PublishJvmToMaven : BuildType({
    createDeploymentBuild("KtorPublishJvmToMavenBuild", "Publish JVM to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        publishToMaven(
            listOf(
                "publishJvmPublicationToMavenRepository",
                "publishKotlinMultiplatformPublicationToMavenRepository",
                "publishMavenPublicationToMavenRepository"
            ),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
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
    createDeploymentBuild("KtorPublishJSToMavenBuild", "Publish JS to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        publishToMaven(
            listOf(
                "publishJsPublicationToMavenRepository"
            ),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
    }
    dependencies {
        snapshot(jsBuild!!) {
        }
        snapshot(PublishJvmToMaven) {
        }
    }
    requirements {
        require(os = linux.agentString, minMemoryMB = 7000)
    }
})

object PublishWindowsNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishWindowsNativeToMavenBuild", "Publish Windows Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        powerShell {
            name = "Get dependencies and environment ready"
            scriptMode = script {
                content = """
                $libcurlSoftware
                """.trimIndent()
            }
        }
        publishToMaven(
            listOf(
                "publishMingwX64PublicationToMavenRepository"
            ),
            gradleParams = "-P\"signing.gnupg.executable=gpg.exe\"",
            os = "Windows"
        )
    }
    dependencies {
        snapshot(nativeWindowsBuild!!) {
        }
        snapshot(PublishJSToMaven) {
        }
    }
    requirements {
        require(windows.agentString, minMemoryMB = 7000)
    }
})

object PublishLinuxNativeToMaven : BuildType({
    createDeploymentBuild("KtorPublishLinuxNativeToMavenBuild", "Publish Linux Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
    }
    steps {
        publishToMaven(
            listOf(
                "publishLinuxX64PublicationToMavenRepository"
            ),
            gradleParams = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
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
    createDeploymentBuild("KtorPublishMacOSNativeToMavenBuild", "Publish Mac Native to Maven", "", releaseVersion)
    vcs {
        root(VCSCore)
    }
    params {
        configureReleaseVersion()
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
                "publishWatchosX86PublicationToMavenRepository"
            ),
            gradleParams = "-Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
        )
    }
    dependencies {
        snapshot(nativeMacOSBuild!!) {
        }
        snapshot(PublishLinuxNativeToMaven) {
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
echo "Stopping gpg-agent and removing GNUPG folder"
Stop-Process -Name "gpg-agent" -ErrorAction SilentlyContinue
rm -r -fo C:\Users\builduser\.gnupg

# Hard-coding path for GPG since this fails on TeamCity
# ${'$'}gpg=(get-command gpg.exe).Path
${'$'}gpg="C:\Program Files\Git\usr\bin\gpg.exe"
Set-Alias -Name gpg2.exe -Value ${'$'}gpg

echo "Exporting public key"
[System.IO.File]::WriteAllText("${'$'}pwd\keyfile", ${'$'}Env:SIGN_KEY_PUBLIC)
& ${'$'}gpg --batch --import keyfile
rm keyfile


echo "Exporting private key"

[System.IO.File]::WriteAllText("${'$'}pwd\keyfile", ${'$'}Env:SIGN_KEY_PRIVATE)
& ${'$'}gpg --allow-secret-key-import --batch --import keyfile
rm keyfile
& ${'$'}gpg --list-keys

echo "Sending keys"
& ${'$'}gpg --keyserver hkp://ha.pool.sks-keyservers.net --send-keys %env.SIGN_KEY_ID%
& ${'$'}gpg --keyserver hkp://keyserver.ubuntu.com --send-keys %env.SIGN_KEY_ID%

& "gpgconf" --kill gpg-agent
& "gpgconf" --homedir "/c/Users/builduser/.gnupg" --launch gpg-agent


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
echo "Sending keys"
gpg --keyserver hkp://ha.pool.sks-keyservers.net --send-keys %env.SIGN_KEY_ID%
gpg --keyserver hkp://keyserver.ubuntu.com --send-keys %env.SIGN_KEY_ID%
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
rm -r -fo C:\Users\builduser\.gnupg
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
            "${gradleTasks.joinToString(" ")} --i -PreleaseVersion=$releaseVersion $gradleParams --stacktrace --no-parallel -Porg.gradle.internal.network.retry.max.attempts=100000"
        jdkHome = "%env.${java11.env}%"
        buildFile = "build.gradle.kts"
    }
    cleanupKeyFile(os)
}
