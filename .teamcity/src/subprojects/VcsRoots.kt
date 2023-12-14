package subprojects

import jetbrains.buildServer.configs.kotlin.v2019_2.Triggers
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

const val defaultBranch = "main"
const val VCSUsername = "hhariri"
const val VCSToken = "%github.token%"

object VCSCore : PasswordVcsRoot({
    name = "Ktor Core"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*)/head
    """.trimIndent()
})

object VCSCoreEAP : PasswordVcsRoot({
    name = "Ktor Core EAP Branches"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/(*-eap)
        +:refs/heads/($defaultBranch)
        -:refs/(pull/*)/head
    """.trimIndent()
})

object VCSDocs : PasswordVcsRoot({
    name = "Ktor documentation"
    url = "https://github.com/ktorio/ktor-documentation.git"
})

object VCSSamples : PasswordVcsRoot({
    name = "Ktor Samples"
    url = "https://github.com/ktorio/ktor-samples.git"
})

object VCSGetStartedSample : PasswordVcsRoot({
    name = "Ktor Get Started Sample"
    url = "https://github.com/ktorio/ktor-get-started.git"
})

object VCSGradleSample : PasswordVcsRoot({
    name = "Ktor Gradle Sample"
    url = "https://github.com/ktorio/ktor-gradle-sample.git"
})

object VCSMavenSample : PasswordVcsRoot({
    name = "Ktor Maven Sample"
    url = "https://github.com/ktorio/ktor-maven-sample.git"
})

object VCSHttpApiSample : PasswordVcsRoot({
    name = "Ktor HTTP API Sample"
    url = "https://github.com/ktorio/ktor-http-api-sample.git"
})

object VCSWebSocketsChatSample : PasswordVcsRoot({
    name = "Ktor Websockets Chat Sample"
    url = "https://github.com/ktorio/ktor-websockets-chat-sample.git"
})

object VCSWebsiteSample : PasswordVcsRoot({
    name = "Ktor Website Sample"
    url = "https://github.com/ktorio/ktor-website-sample.git"
})

object VCSAPIDocs : PasswordVcsRoot({
    name = "API Docs"
    url = "https://github.com/ktorio/api.ktor.io.git"
})

object VCSKotlinxHtml : PasswordVcsRoot({
    name = "Kotlinx.html Library"
    url = "https://github.com/Kotlin/kotlinx.html.git"
    branch = "master"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*)/head
    """.trimIndent()
})

object VCSKtorBenchmarks : PasswordVcsRoot({
    name = "Ktor Benchmarks"
    url = "https://github.com/ktorio/ktor-benchmarks.git"
})

object VCSPluginRegistry : PasswordVcsRoot({
    name = "Ktor Plugin Registry"
    url = "https://github.com/ktorio/ktor-plugin-registry.git"
    branch = "main"
})

object VCSKtorGeneratorWebsite : PasswordVcsRoot({
    name = "Ktor Generator Website"
    url = "https://github.com/ktorio/ktor-generator-website.git"
    branch = "master"
})

object VCSKtorCLI : PasswordVcsRoot({
    name = "Ktor CLI"
    url = "https://github.com/ktorio/ktor-cli.git"
    branch = "main"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*)/head
    """.trimIndent()
})

object VCSKtorBuildPlugins : PasswordVcsRoot({
    name = "Ktor Build Plugins"
    url = "https://github.com/ktorio/ktor-build-plugins.git"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*)/head
    """.trimIndent()
})

open class KtorVcsRoot(init: GitVcsRoot.() -> Unit) : GitVcsRoot({
    userNameStyle = UserNameStyle.NAME
    branch = defaultBranch

    init()
})

open class PasswordVcsRoot(init: GitVcsRoot.() -> Unit) : KtorVcsRoot({
    authMethod = password {
        userName = VCSUsername
        password = VCSToken
    }

    init()
})

fun Triggers.onChangeAllBranchesTrigger() {
    vcs {
        triggerRules = """
                            -:*.md
                            -:.gitignore
        """.trimIndent()
        branchFilter = """
                            +:*
                            -:pull/*
                            -:dependabot/*
                            -:renovate/*
        """.trimIndent()
    }
}

fun Triggers.nightlyEAPBranchesTrigger() {
    schedule {
        schedulingPolicy = daily {
            hour = 20
        }
        triggerBuild = always()
    }
}
