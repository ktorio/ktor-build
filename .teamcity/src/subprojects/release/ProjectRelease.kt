package subprojects.release

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.*
import subprojects.build.*
import subprojects.release.apidocs.ProjectReleaseAPIDocs
import subprojects.release.publishing.*
import java.io.*

object ProjectRelease : Project({
    id("ProjectKtorRelease")
    name = "Release Ktor"
    description = " The Full Monty! - Release Ktor framework, update docs, site, etc."

    subProject(ProjectReleaseAPIDocs)
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
        param("env.SIGN_KEY_PUBLIC", value = """-----BEGIN PGP PUBLIC KEY BLOCK-----

mQGNBF+TCd4BDACbIA94MfIWL0SpvZwBddXgx36Lp9GYOWNgGoQCWSvk9vaMrLaI
rEll0xnoP98CfBQYrVSAmHDMhSLBCjNB3V1Sdz8GRdOG7HUffF7Cqwbm3Fxo3H/h
+Tsrodv23NuvKsDpgglUL6nJy5e/FO8y9dcxLXRRVdPFDhJubi08SiUJy9FQbnfA
yb2LuTzXtjDmjEsMZpdpQUlQkk0xNDkrrq+2miwxemVd35cnVQCFP0K7c4T0ksGg
Rf9A2r45DBbPfvwTL+ZbrGtCssUpCneWhPl79UsMxeY+vJjEggqqqRqbHRn6nOQd
3gKSaEqdALZURPzvkKxLUeUUtMk/tkFdsNe/ea7edk6G3MI4dbUY7p0XLS54S9cB
1JUAHNEFtuJQKGWNuwWO58Yun1EBtOdUEvnIIoQ+CIN/XeKrnEIXE3LSblB8BR3H
bqX54BMe9AzsmDQtc5pUOm2pfvCoiv8xFXQznBg24dGqo2A/jMoUnGj6oRj7k8mt
i9AdPLigldr0S0sAEQEAAbQhS3RvciBSZWxlYXNlIDxrdG9yQGpldGJyYWlucy5j
b20+iQHUBBMBCgA+FiEEOUy0NsVpFvwB7qSnfDD3sTKduocFAl+TCd4CGwEFCQPC
ZwAFCwkIBwMFFQoJCAsFFgIDAQACHgECF4AACgkQfDD3sTKduofP5gv/WRDpats/
AbkXtXF90tNmdLomqzrLaz2dmsmekVjHnppEDZAJDWgHKuqi2LL+FU+8RPZj91yE
rln0/LmOb1gGAkYhL5HIdSKPhd1BYrlObYIvxoarEi/U3+92B+13621qu4GEV96e
luRXXe85vncuZiwjwPQqmST8gsAD17AiRt71jUHCqQUhsEE3C/5btOrqvM0Bhh+3
QDUEoJcl1KoQjkPYhSDP630jhgsq0E22Yb1CWSXbwlJZTpmidAKICurll6YFhHQN
wL5CLj+DjBJfkyC6nRDK1fX1IyU5jN201iDYMh+ibUclJFF4Lwl/ISlb/8NdhbPS
SnNehscKyAK+xZ0w9CVfTVOIl0qx4SgwfoWu9fi02vQe60fK82usbrNJl+jWHAJY
FcZjQ70cU0JpFVwhWr0ffOLD9A+HhiqzL7SBASapY4w5yLSlqZ5BOKcZx7NVqtXI
qPgmbrSIYoXEzz4HQb+oCeXH6AigT+pxZJXpyEQudIaBtC67Nern3mYbuQGNBF+T
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
=Cwlo
-----END PGP PUBLIC KEY BLOCK-----
"""
        )
    }
})

fun ParametrizedWithType.configureReleaseVersion() {
    text("releaseVersion", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
}