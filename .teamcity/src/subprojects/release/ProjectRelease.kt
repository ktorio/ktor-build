package subprojects.release

import jetbrains.buildServer.configs.kotlin.*
import subprojects.*
import subprojects.build.*
import subprojects.release.apidocs.*
import subprojects.release.generator.*
import subprojects.release.publishing.*
import java.io.*

object ProjectRelease : Project({
    id("ProjectKtorRelease")
    name = "Release Ktor"
    description = " The Full Monty! - Release Ktor framework, update docs, site, etc."

    subProject(ProjectReleaseAPIDocs)
    subProject(ProjectReleaseGeneratorWebsite)
    subProject(ProjectPublishing)

    buildType(ReleaseBuild)

    params {
        defaultTimeouts()
        param("env.SIGN_KEY_ID", value = "0x7c30f7b1329dba87")

        // Inherited from parent project. The reason for this is that security tokens seem to mess up with multiline values
        // So we set this in the parent project and read from there. That way, we don't need to make our project editable.
        password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
        password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
        password("env.PUBLISHING_USER", value = "%sonatype.username%")
        password("env.PUBLISHING_PASSWORD", value = "%sonatype.password%")
        param("env.PUBLISHING_URL", value = "%sonatype.url%")
        param("env.SIGN_KEY_LOCATION", value = File("%teamcity.build.checkoutDir%").invariantSeparatorsPath)
        param("env.SIGN_KEY_PUBLIC", value = SIGN_KEY_PUBLIC)
    }
})

fun ParametrizedWithType.configureReleaseVersion() {
    text("releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
}
