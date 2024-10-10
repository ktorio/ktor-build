package subprojects.release.publishing

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

internal val MACOS_GRADLE_ARGS =
    "--parallel -Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
