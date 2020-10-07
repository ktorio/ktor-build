package subprojects.build.docsamples

import VCSDocs
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle

const val relativeDir = "codeSnippets"

object ProjectDocSamples : Project({
  id("ProjectKtorDocs")
  name = "Docs"
  description = "Code samples included in Docs and API Docs"

  buildType {
    id("KtorDocs_BuildTest")
    name = "Build and test code samples"

    vcs {
      root(VCSDocs)
    }

    steps {
      gradle {
        tasks = "clean build"
        workingDir = relativeDir
      }
      gradle {
        tasks = "test"
        workingDir = relativeDir
      }
    }
  }
})