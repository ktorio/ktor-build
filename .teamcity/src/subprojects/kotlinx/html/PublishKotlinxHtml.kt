package subprojects.kotlinx.html

import jetbrains.buildServer.configs.kotlin.v2019_2.*

object PublishKotlinxHtml : Project({
    id("ProjectKotlinxHtml")
    name = "Release kotlinx.html"

    publishToSpace()
    publishToCentral()
})

