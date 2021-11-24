package subprojects.build.core

import jetbrains.buildServer.configs.kotlin.v10.toExtId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.PowerShellStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import subprojects.VCSCore
import subprojects.build.*
import subprojects.onChangeAllBranchesTrigger
import subprojects.release.jvmBuild

class CoreBuild(private val osJdkEntry: OSJDKEntry) : BuildType({
    id("KtorMatrixCore_${osJdkEntry.osEntry.name}${osJdkEntry.jdkEntry.name}".toExtId())
    name = "${osJdkEntry.jdkEntry.name} on ${osJdkEntry.osEntry.name}"
    val artifactsToPublish = formatArtifacts("+:**/build/**/*.jar")
    artifactRules = formatArtifacts(artifactsToPublish, junitReportArtifact, memoryReportArtifact)
    vcs {
        root(VCSCore)
    }
    triggers {
        onChangeAllBranchesTrigger()
    }
    steps {
        if (osJdkEntry.osEntry == windows) {
            defineTCPPortRange()
            installJdk7OnWindows()
        } else if (osJdkEntry.osEntry == linux) {
            installJdk7OnLinux()
            script {
                name = "Obtain Library Dependencies"
                scriptContent = libSoftware
            }
        }
        gradle {
            name = "Build and Run Tests"
            buildFile = "build.gradle.kts"
            tasks = "cleanJvmTest jvmTest --no-parallel --continue --info"
            jdkHome = "%env.${osJdkEntry.jdkEntry.env}%"
        }
    }

    defaultBuildFeatures(VCSCore.id.toString())

    requirements {
        require(os = osJdkEntry.osEntry.agentString, minMemoryMB = 7000)
    }
    if (osJdkEntry.osEntry == linux && osJdkEntry.jdkEntry == java11) {
        jvmBuild = this
    }
})

fun BuildSteps.defineTCPPortRange() {
    script {
        scriptContent = "netsh int ipv4 set dynamicport tcp start=1024 num=64510"
    }
}

fun formatArtifacts(vararg artifacts: String): String {
    return artifacts.joinToString("\n")
}

fun BuildSteps.installJdk7OnLinux() {
    val VERSION = "\$VERSION"
    val ARCH = "\$ARCH"
    val DIR = "\$DIR"
    val FILE = "\$FILE"

    val script = """
        #!/bin/bash

        export AWS_DEFAULT_REGION=eu-west-1
        aws s3 cp s3://common-teamcityhostedprod/plugins/compassionate-ptolemy-6wkpld/jdk-7.79-linux_x64.tar.gz ./ --only-show-errors

        VERSION=7.79
        ARCH=x64
        FILE=jdk-$VERSION-linux_$ARCH.tar.gz
        DIR="%system.agent.home.dir%/jdk-$VERSION-$ARCH"
        mkdir -p ~/bin

        [[ -d "$DIR" ]] && rm -rf "$DIR"
        mkdir -p "$DIR"
        tar -xzf "./$FILE" --strip-components=1 -C "$DIR"
        rm "./$FILE"

        cd $DIR/bin
        ./java -version

        echo "##teamcity[setParameter name='env.JDK_1_7' value='${'$'}(pwd)']"
    """.trimIndent()

    script {
        name = "Install JDK7 on Linux"
        scriptContent = script
    }
}

fun BuildSteps.installJdk7OnWindows() {
    val D = "\$"
    val scriptContent = """
        
        ${D}version = "7.79"
        ${D}arch = "x64"
        ${D}file = "jdk-${D}version-windows_${D}arch.exe"
        ${D}url = "s3://common-teamcityhostedprod/plugins/compassionate-ptolemy-6wkpld/${D}file"

        Write-Host "* Downloading JDK from S3 bucket ${D}url"
        ${D}path = "${D}env:temp\${'$'}file"
        aws s3 cp ${D}url ${D}path --only-show-errors

        ${D}jdkdir = "C:\tools\jdk${D}version-${D}arch"

        Write-Host "* Installing JDK ${D}version (${D}arch)"
        ${D}args = "/s ADDLOCAL=ToolsFeature INSTALLDIR=${D}jdkdir"
        ${D}process = (Start-Process -Wait -PassThru ${D}path -ArgumentList ${D}args)
        ${D}process.WaitForExit()
        if (${D}process.ExitCode -ne 0) {
            throw "Exec: Unable to install jdk: exit code " + ${D}process.ExitCode
        }
        echo "##teamcity[setParameter name='env.JDK_1_7' value='${D}jdkdir\bin']"
    """.trimIndent()

    powerShell {
        name = "Install JDK7 on Windows"
        scriptMode = PowerShellStep.ScriptMode.Script().apply {
            content = scriptContent
        }

        script {
            content = scriptContent
        }
    }
}
