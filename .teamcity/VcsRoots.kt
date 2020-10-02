import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.*

object VCSCore : GitVcsRoot({
  name = "Ktor"
  url = "https://github.com/ktorio/ktor.git"
  authMethod = password {
    userName = "hhariri"
    password = "credentialsJSON:a48648d8-f9b1-4720-bef0-85445fe9171f"
  }
})

object VCSSamples: GitVcsRoot({
  name = "Ktor Samples"
  url = "https://github.com/ktorio/ktor-samples.git"
})
