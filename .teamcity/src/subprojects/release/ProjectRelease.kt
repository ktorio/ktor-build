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
    subProject(ProjectPublishingProtected)

    buildType(ReleaseBuild)

    params {
        defaultGradleParams()
        publishingCredentials()
    }
})

fun ParametrizedWithType.publishingCredentials() {
    signingCredentials()
    mavenCentralCredentials()
}

/**
 * The signing key of the builds. A parent project of the settings must define `sign.key.id`, `sign.key.public`,
 * `sign.key.passphrase` and `sign.key.private`.
 */
fun ParametrizedWithType.signingCredentials() {
    param("env.SIGN_KEY_ID", value = "%sign.key.id%")

    // Inherited from parent project. The reason for this is that security tokens seem to mess up with multiline values
    // So we set this in the parent project and read from there. That way, we don't need to make our project editable.
    password("env.SIGN_KEY_PASSPHRASE", value = "%sign.key.passphrase%")
    password("env.SIGN_KEY_PRIVATE", value = "%sign.key.private%")
    param("env.SIGN_KEY_LOCATION", value = File("%teamcity.build.checkoutDir%").invariantSeparatorsPath)
    param("env.SIGN_KEY_PUBLIC", value = "%sign.key.public%")
}

fun ParametrizedWithType.mavenCentralCredentials() {
    password("env.ORG_GRADLE_PROJECT_mavenCentralUsername", value = "%centralPortal.username%")
    password("env.ORG_GRADLE_PROJECT_mavenCentralPassword", value = "%centralPortal.password%")
}

fun ParametrizedWithType.configureReleaseVersion() {
    text("releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
}
