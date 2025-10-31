package subprojects.release.publishing

private const val VALIDATE_PUBLISHED_ARTIFACTS = "validatePublishedArtifacts"
internal const val JVM_AND_COMMON_PUBLISH_TASK = "$VALIDATE_PUBLISHED_ARTIFACTS publishJvmAndCommonPublications"
internal const val JS_PUBLISH_TASK = "$VALIDATE_PUBLISHED_ARTIFACTS publishJsPublications"
internal const val LINUX_PUBLISH_TASK = "$VALIDATE_PUBLISHED_ARTIFACTS publishLinuxPublications"
internal const val WINDOWS_PUBLISH_TASK = "$VALIDATE_PUBLISHED_ARTIFACTS publishWindowsPublications"
internal const val DARWIN_PUBLISH_TASK = "$VALIDATE_PUBLISHED_ARTIFACTS publishDarwinPublications"
internal const val ANDROID_NATIVE_PUBLISH_TASK = "$VALIDATE_PUBLISHED_ARTIFACTS publishAndroidNativePublications"

internal const val EXCLUDE_DOKA_GENERATION = "-x dokkaGeneratePublicationHtml"

internal val MACOS_PUBLISH_TASKS = listOf(
    "publishIosArm64PublicationToMavenRepository",
    "publishIosX64PublicationToMavenRepository",
    "publishIosSimulatorArm64PublicationToMavenRepository",

    "publishMacosX64PublicationToMavenRepository",
    "publishMacosArm64PublicationToMavenRepository",

    "publishTvosArm64PublicationToMavenRepository",
    "publishTvosX64PublicationToMavenRepository",
    "publishTvosSimulatorArm64PublicationToMavenRepository",

    "publishWatchosArm32PublicationToMavenRepository",
    "publishWatchosArm64PublicationToMavenRepository",
    "publishWatchosX64PublicationToMavenRepository",
    "publishWatchosSimulatorArm64PublicationToMavenRepository",
    "publishWatchosDeviceArm64PublicationToMavenRepository",
)

internal const val GPG_DEFAULT_GRADLE_ARGS = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
internal const val GPG_MACOS_GRADLE_ARGS = "-Psigning.gnupg.executable=gpg $GPG_DEFAULT_GRADLE_ARGS"
internal const val GPG_WINDOWS_GRADLE_ARGS = "-P\"signing.gnupg.executable=gpg.exe\""
