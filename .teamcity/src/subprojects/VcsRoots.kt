package subprojects

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*

const val defaultBranch = "main"
const val VCSUsername = "hhariri"
const val VCSToken = "credentialsJSON:a48648d8-f9b1-4720-bef0-85445fe9171f"

object VCSCore : PasswordVcsRoot({
    name = "Ktor"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*)/head
    """.trimIndent()
})

object VCSCoreEAP : PasswordVcsRoot({
    name = "Ktor EAP Branches"
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
                        """.trimIndent()
        quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_DEFAULT
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
