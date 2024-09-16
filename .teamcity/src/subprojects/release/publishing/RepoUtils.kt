package subprojects.release.publishing

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*

internal fun BuildSteps.createSonatypeRepository(task: String) {
    script {
        name = "Create sonatype repository"
        scriptContent = """
                curl -X POST https://oss.sonatype.org/service/local/staging/profiles/7e2f1cfcaa55a1/start \
                    -u %sonatype.username%:%sonatype.password% \
                    -H "Content-Type: application/xml" \
                    -H "Accept: application/xml" \
                    -d "<promoteRequest><data><description>Repository for publishing Ktor $task</description></data></promoteRequest>" \
                    | grep -o 'ioktor-[0-9]*' > repo.xml

                echo "##teamcity[setParameter name='env.REPOSITORY_ID' value='${'$'}(cat repo.xml | grep -o 'ioktor-[0-9]*')']"
        """.trimIndent()
    }
}
