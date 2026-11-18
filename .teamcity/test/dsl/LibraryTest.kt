package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.vcs.*
import org.junit.Test
import subprojects.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.build.gradle.*
import subprojects.release.*
import kotlin.io.path.*
import kotlin.test.*

class LibraryTest {
    private val vcsRoot = GitVcsRoot {
        id("IsolatedSource")
        name = "Isolated source"
        url = "https://example.test/source.git"
        branch = "refs/heads/main"
    }
    private val repo = KtorRepository(vcsRoot, "Isolated_", "Isolated ")

    @Test
    fun `environment uses the repository root and ID`() {
        val environment = GradleEnvironment(Agents.OS.Linux, Agents.Arch.X64, setOf(GradleUserHome.Light), repo)

        assertEquals("Isolated_GradleEnvironment_Linux", environment.id.toString())
        assertEquals(listOf(vcsRoot.id), environment.vcs.entries.map { it.root.id })
        val rules = environment.triggers.items.first().params.first { it.name == "triggerRules" }.value
        assertTrue(rules.contains("root=IsolatedSource:"))
        assertFalse(rules.contains("root=VCSCore:"))
    }

    @Test
    fun `verification builds use only their own environment artifacts`() {
        val builds = createCoreVerificationBuilds(repo)
        val consumers = builds.builds.filterIsInstance<JDKBuild>() + builds.jvmCompileBuild

        assertTrue(builds.builds.isNotEmpty())
        for (build in consumers) {
            val ids = build.dependencies.items.map { it.buildTypeId.id.toString() }
            assertTrue("Isolated_GradleEnvironment_Linux" in ids, "${build.name}: $ids")
            assertFalse("GradleEnvironment_Linux" in ids, "${build.name}: $ids")
        }
    }

    @Test
    fun `scripts load from the classpath without a scripts directory`() {
        assertFalse(Path("scripts/cancel_previous_builds.sh").exists())
        val build = JvmCompileBuild(repo)
        val step = build.steps.items.filterIsInstance<ScriptBuildStep>().single { it.name == "Cancel Previous Builds" }
        val resource = requireNotNull(javaClass.getResource("/scripts/cancel_previous_builds.sh")).readText()

        assertEquals(resource, step.scriptContent)
    }

    @Test
    fun `scripts in the working directory override classpath resources`() {
        val path = Path("scripts/cancel_previous_builds.sh")
        path.parent.createDirectories()
        try {
            path.writeText("echo local-script")
            val build = JvmCompileBuild(repo)
            val step = build.steps.items.filterIsInstance<ScriptBuildStep>().single { it.name == "Cancel Previous Builds" }

            assertEquals("echo local-script", step.scriptContent)
        } finally {
            path.deleteIfExists()
            path.parent.deleteIfExists()
        }
    }

    @Test
    fun `signing credentials exclude Maven Central credentials`() {
        val project = Project { params { signingCredentials() } }
        val names = project.params.params.map { it.name }

        assertTrue("env.SIGN_KEY_PRIVATE" in names)
        assertTrue("env.SIGN_KEY_PASSPHRASE" in names)
        assertFalse(names.any { it.contains("mavenCentral") })
    }

    @Test
    fun `public publishing retains signing and Maven Central credentials`() {
        val project = Project { params { publishingCredentials() } }
        val names = project.params.params.map { it.name }

        assertTrue("env.SIGN_KEY_PRIVATE" in names)
        assertTrue("env.ORG_GRADLE_PROJECT_mavenCentralUsername" in names)
        assertTrue("env.ORG_GRADLE_PROJECT_mavenCentralPassword" in names)
    }
}
