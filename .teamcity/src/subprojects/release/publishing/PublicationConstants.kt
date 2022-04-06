package subprojects.release.publishing

internal val MACOS_PUBLISH_TASKS = listOf(
    "publishIosArm32PublicationToMavenRepository",
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
    "publishWatchosX86PublicationToMavenRepository",
    "publishWatchosX64PublicationToMavenRepository",
    "publishWatchosSimulatorArm64PublicationToMavenRepository"
)

internal val MACOS_GRADLE_ARGS =
    "--parallel -Psigning.gnupg.executable=gpg -Psigning.gnupg.homeDir=%env.SIGN_KEY_LOCATION%/.gnupg"
