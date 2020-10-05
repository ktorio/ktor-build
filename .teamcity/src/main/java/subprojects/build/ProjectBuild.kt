package subprojects.build

import VCSCore
import VCSSamples
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import subprojects.build.core.*
import subprojects.build.docsamples.*
import subprojects.build.generator.*
import subprojects.build.plugin.*
import subprojects.build.samples.*

object ProjectBuild : Project({
  id("ProjectKtorBuild")
  name = "Build"
  description = "Build configurations that build Ktor"

  vcsRoot(VCSCore)
  vcsRoot(VCSSamples)

  subProject(ProjectGenerator)
  subProject(ProjectSamples)
  subProject(ProjectCore)
  subProject(ProjectDocSamples)
  subProject(ProjectPlugin)
})
