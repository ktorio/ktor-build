package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.release.*

const val relativeDir = "codeSnippets"

object ProjectDocSamples : Project({
  id("ProjectKtorDocs")
  name = "Docs"
  description = "Code samples included in Docs and API Docs"

  samplesBuild = buildType {
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
        buildFile = "build.gradle"
      }
      gradle {
        name = "Test"
        tasks = "test"
        workingDir = relativeDir
        buildFile = "build.gradle"
      }
    }
  }
})
