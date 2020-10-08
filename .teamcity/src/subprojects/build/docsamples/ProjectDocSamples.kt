package subprojects.build.docsamples

import VCSDocs
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

const val relativeDir = "codeSnippets"

object ProjectDocSamples : Project({
  id("ProjectKtorDocs")
  name = "Docs"
  description = "Code samples included in Docs and API Docs"

  vcsRoot(VCSDocs)

  buildType {
    id("KtorDocs_ValidateSamples")
    name = "Build and test code samples"

    vcs {
      root(VCSDocs)
    }

    steps {
      gradle {
        name = "Build"
        tasks = "clean build"
        workingDir = relativeDir
      }
      gradle {
        name = "Test"
        tasks = "test"
        workingDir = relativeDir
      }
    }
  }
})