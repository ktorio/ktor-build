package subprojects

import jetbrains.buildServer.configs.kotlin.vcs.*

const val VCSUsername = "hhariri"
const val VCSToken = "%github.token%"

private const val defaultBranch = "refs/heads/(main)"
private const val defaultBranchRef = "refs/heads/main" // Reference without braces around logical name
private const val DefaultAndPullRequests = "+:$defaultBranch\n+:refs/(pull/*)/head"
private const val AllBranchesAndPullRequests = "+:refs/heads/*\n+:refs/(pull/*)/head"

object VCSCore : PasswordVcsRoot({
    name = "Ktor Core"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = AllBranchesAndPullRequests
})

object VCSCoreEAP : PasswordVcsRoot({
    name = "Ktor Core EAP Branches"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/(*-eap)
        +:$defaultBranch
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
    branch = "refs/heads/master"
    branchSpec = AllBranchesAndPullRequests
})

object VCSKtorBenchmarks : PasswordVcsRoot({
    name = "Ktor Benchmarks"
    url = "https://github.com/ktorio/ktor-benchmarks.git"
})

object VCSPluginRegistry : PasswordVcsRoot({
    name = "Ktor Plugin Registry"
    url = "https://github.com/ktorio/ktor-plugin-registry.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorGeneratorBackend : PasswordVcsRoot({
    name = "Ktor Generator Backend"
    url = "https://github.com/ktorio/ktor-generator-backend.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorGeneratorWebsite : PasswordVcsRoot({
    name = "Ktor Generator Website"
    url = "https://github.com/ktorio/ktor-generator-website.git"
    branch = "refs/heads/master"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorCLI : PasswordVcsRoot({
    name = "Ktor CLI"
    url = "https://github.com/ktorio/ktor-cli.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorBuildPlugins : PasswordVcsRoot({
    name = "Ktor Build Plugins"
    url = "https://github.com/ktorio/ktor-build-plugins.git"
    branchSpec = DefaultAndPullRequests
})

open class KtorVcsRoot(init: GitVcsRoot.() -> Unit) : GitVcsRoot({
    userNameStyle = UserNameStyle.USERID
    branch = defaultBranchRef

    init()
})

open class PasswordVcsRoot(init: GitVcsRoot.() -> Unit) : KtorVcsRoot({
    authMethod = password {
        userName = VCSUsername
        password = VCSToken
    }

    init()
})
