package subprojects

import jetbrains.buildServer.configs.kotlin.vcs.*

private object VcsToken {
    const val KTOR = "credentialsJSON:888104f9-4e94-4807-8988-911d637473fd"
    const val DOCS_AND_SAMPLES = "credentialsJSON:46bc4b63-b160-4f56-9b7a-14336fd0efc4"
    const val PROJECT_GENERATOR = "credentialsJSON:bfc697ba-583e-4e72-9f45-8b06a4e47f0a"
    const val BENCHMARKS = "credentialsJSON:0605d122-42e4-4982-b81c-1ba96c0d2296"
    const val KTOR_CLI = "credentialsJSON:0e185b37-2b92-4a5e-87bd-60c892ef0d30"
    const val BUILD_PLUGINS = "credentialsJSON:d8465f79-fea1-4f49-883f-7b0b7e788ee8"
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
