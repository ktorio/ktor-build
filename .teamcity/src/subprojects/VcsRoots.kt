package subprojects

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.*
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*
import java.io.*

const val defaultBranch = "master"
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
        +:*-eap
        +:$defaultBranch
        -:refs/(pull/*)/head
    """.trimIndent()
})

object VCSDocs : PasswordVcsRoot({
    name = "Ktor documentation"
    url = "https://github.com/ktorio/ktor-documentation.git"
})

object VCSSamples : GitVcsRoot({
    name = "Ktor Samples"
    url = "https://github.com/ktorio/ktor-samples.git"
})

object VCSAPIDocs : PasswordVcsRoot({
    name = "API Docs"
    url = "https://github.com/ktorio/api.ktor.io.git"
})

open class KtorVcsRoot(init: GitVcsRoot.() -> Unit) : GitVcsRoot({
    init()
    userNameStyle = UserNameStyle.NAME
})

open class PasswordVcsRoot(init: GitVcsRoot.() -> Unit) : KtorVcsRoot({
    init()
    authMethod = password {
        userName = VCSUsername
        password = VCSToken
    }
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
        triggerRules = """
                            -:*.md
                            -:.gitignore
                        """.trimIndent()
        branchFilter = """
                    +:*-eap
                    +:$defaultBranch
                    -:refs/(pull/*)/head
        """.trimIndent()
        triggerBuild = always()
    }
}
