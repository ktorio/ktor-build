package subprojects.release.publishing

// JVM artifacts + multiplatform metadata
internal val JVM_PUBLISH_TASKS = listOf(
    "publishJvmPublicationToMavenRepository",
    "publishKotlinMultiplatformPublicationToMavenRepository",
    "publishMavenPublicationToMavenRepository",
)

internal val LINUX_PUBLISH_TASKS = listOf(
    "publishLinuxX64PublicationToMavenRepository",
    "publishLinuxArm64PublicationToMavenRepository",
)

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

internal val ANDROID_NATIVE_PUBLISH_TASKS = listOf(
    "publishAndroidNativeArm64PublicationToMavenRepository",
    "publishAndroidNativeArm32PublicationToMavenRepository",
    "publishAndroidNativeX64PublicationToMavenRepository",
    "publishAndroidNativeX86PublicationToMavenRepository",
)

internal const val GPG_DEFAULT_GRADLE_ARGS = "-Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
internal const val GPG_MACOS_GRADLE_ARGS = "-Psigning.gnupg.executable=gpg $GPG_DEFAULT_GRADLE_ARGS"
internal const val GPG_WINDOWS_GRADLE_ARGS = "-P\"signing.gnupg.executable=gpg.exe\""
