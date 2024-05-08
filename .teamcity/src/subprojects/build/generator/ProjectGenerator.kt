package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.v2019_2.*

object ProjectGenerator : Project({
    id("ProjectKtorGenerator")
    name = "Project Generator"
    description = "Code for start.ktor.io"

    /**
     *
     */
    buildType(PublishPluginRegistry)

    /**
     * Tests registry, and ensures any new plugins can be built.
     */
    buildType(TestPluginRegistry)

    /**
     * Runs on every PR for ktor-generator-website.
     */
    buildType(BuildGeneratorWebsite)

})


