package subprojects.build.docsamples

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import subprojects.*
import subprojects.Agents.ANY
import subprojects.Agents.Arch
import subprojects.Agents.OS
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

        requirements {
            agent(OS.Linux, Arch.X64, hardwareCapacity = ANY)
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
