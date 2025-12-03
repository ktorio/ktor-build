package subprojects

import jetbrains.buildServer.configs.kotlin.vcs.*

private object VcsToken {
    const val KTOR = "tc_token_id:CID_821a66c1c2972c7bca80580557b4a475:-1:cc9c11b1-ed43-415e-b2f8-129a86f64a3b"
    const val DOCS_AND_SAMPLES = "tc_token_id:CID_821a66c1c2972c7bca80580557b4a475:-1:b9145cff-b66c-4bf7-9911-501c38d00274"
    const val PROJECT_GENERATOR = "tc_token_id:CID_821a66c1c2972c7bca80580557b4a475:-1:20b5312f-608e-46d3-9c83-41e00a2006ec"
    const val BENCHMARKS = "tc_token_id:CID_821a66c1c2972c7bca80580557b4a475:-1:6b3b0c69-150d-46dd-9cd9-14d05921e87a"
    const val KTOR_CLI = "tc_token_id:CID_821a66c1c2972c7bca80580557b4a475:-1:144c067e-37f6-4a71-a0aa-ffa2f5a2c8ba"
    const val BUILD_PLUGINS = "tc_token_id:CID_821a66c1c2972c7bca80580557b4a475:-1:23b77452-aa39-436d-b945-7188149290c8"
}

private const val defaultBranch = "refs/heads/(main)"
private const val releaseBranches = "refs/heads/(release/*)"
private const val defaultBranchRef = "refs/heads/main" // Reference without braces around logical name
private const val DefaultAndPullRequests = "+:$defaultBranch\n+:$releaseBranches\n+:refs/(pull/*)/head"
private const val AllBranchesAndPullRequests = "+:refs/heads/*\n+:refs/(pull/*)/head"

object VCSCore : TokenVcsRoot(VcsToken.KTOR, {
    name = "Ktor Core"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = AllBranchesAndPullRequests
})

object VCSCoreEAP : TokenVcsRoot(VcsToken.KTOR, {
    name = "Ktor Core EAP Branches"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/(*-eap)
        +:$releaseBranches
        +:$defaultBranch
    """.trimIndent()
})

object VCSDocs : TokenVcsRoot(VcsToken.DOCS_AND_SAMPLES, {
    name = "Ktor documentation"
    url = "https://github.com/ktorio/ktor-documentation.git"
})

object VCSSamples : TokenVcsRoot(VcsToken.DOCS_AND_SAMPLES, {
    name = "Ktor Samples"
    url = "https://github.com/ktorio/ktor-samples.git"
})

object VCSAPIDocs : TokenVcsRoot(VcsToken.DOCS_AND_SAMPLES, {
    name = "API Docs"
    url = "https://github.com/ktorio/api.ktor.io.git"
    branchSpec = AllBranchesAndPullRequests
})

@Suppress("DEPRECATION")
object VCSKotlinxHtml : PasswordVcsRoot({
    name = "Kotlinx.html Library"
    url = "https://github.com/Kotlin/kotlinx.html.git"
    branch = "refs/heads/master"
    branchSpec = AllBranchesAndPullRequests
})

object VCSKtorBenchmarks : TokenVcsRoot(VcsToken.BENCHMARKS, {
    name = "Ktor Benchmarks"
    url = "https://github.com/ktorio/ktor-benchmarks.git"
})

object VCSPluginRegistry : TokenVcsRoot(VcsToken.PROJECT_GENERATOR, {
    name = "Ktor Plugin Registry"
    url = "https://github.com/ktorio/ktor-plugin-registry.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorGeneratorBackend : TokenVcsRoot(VcsToken.PROJECT_GENERATOR, {
    name = "Ktor Generator Backend"
    url = "https://github.com/ktorio/ktor-generator-backend.git"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorGeneratorWebsite : TokenVcsRoot(VcsToken.PROJECT_GENERATOR, {
    name = "Ktor Generator Website"
    url = "https://github.com/ktorio/ktor-generator-website.git"
    branch = "refs/heads/master"
    branchSpec = DefaultAndPullRequests
})

object VCSKtorCLI : TokenVcsRoot(VcsToken.KTOR_CLI, {
    name = "Ktor CLI"
    url = "https://github.com/ktorio/ktor-cli.git"
    branchSpec = "+:refs/tags/*"
    useTagsAsBranches = true
})

object VCSKtorBuildPlugins : TokenVcsRoot(VcsToken.BUILD_PLUGINS, {
    name = "Ktor Build Plugins"
    url = "https://github.com/ktorio/ktor-build-plugins.git"
    branchSpec = "+:*"
})

object VCSKtorBuildPluginsEAP : TokenVcsRoot(VcsToken.BUILD_PLUGINS, {
    name = "Ktor Build Plugins EAP"
    url = "https://github.com/ktorio/ktor-build-plugins.git"
    branchSpec = """
        +:refs/heads/(*-eap)
        +:$defaultBranch
    """.trimIndent()
})

open class TokenVcsRoot(token: String, init: GitVcsRoot.() -> Unit) : KtorVcsRoot({
    authMethod = token {
        userName = "oauth2"
        tokenId = token
    }

    init()
})

open class KtorVcsRoot(init: GitVcsRoot.() -> Unit) : GitVcsRoot({
    userNameStyle = UserNameStyle.USERID
    branch = defaultBranchRef

    init()
})
@Deprecated("Use GitHub App refreshable token instead")
const val VCSUsername = "hhariri"
@Deprecated("Use GitHub App refreshable token instead")
const val VCSToken = "%github.token%"

@Deprecated("Use GitHubAppVcsRoot instead")
open class PasswordVcsRoot(init: GitVcsRoot.() -> Unit) : KtorVcsRoot({
    @Suppress("DEPRECATION")
    authMethod = password {
        userName = VCSUsername
        password = VCSToken
    }

    init()
})
