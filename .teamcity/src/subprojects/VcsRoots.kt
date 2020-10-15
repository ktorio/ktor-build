package subprojects

import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*

object VCSCore : PasswordVcsRoot({
    name = "Ktor"
    url = "https://github.com/ktorio/ktor.git"
    branchSpec = """
        +:refs/heads/*
        +:refs/(pull/*)/head
    """.trimIndent()
})

object VCSDocs : PasswordVcsRoot({
    name = "Ktor documentation"
    url = "https://github.com/ktorio/ktor-documentation.git"
})

object VCSSamples: GitVcsRoot({
    name = "Ktor Samples"
    url = "https://github.com/ktorio/ktor-samples.git"
})

open class KtorVcsRoot(init: GitVcsRoot.() -> Unit): GitVcsRoot({
    init()
    userNameStyle = UserNameStyle.NAME
})

open class PasswordVcsRoot(init: GitVcsRoot.() -> Unit): KtorVcsRoot( {
    init()
    authMethod = password {
        userName = "hhariri"
        password = "credentialsJSON:a48648d8-f9b1-4720-bef0-85445fe9171f"
    }
})