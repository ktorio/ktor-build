package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.vcs.*
import org.junit.Test
import subprojects.*
import subprojects.build.core.*
import subprojects.release.*
import subprojects.release.publishing.*
import kotlin.io.path.*
import kotlin.test.*

/** Checks what a project needs to use the DSL as a library. Tests run outside the settings directory, see `pom.xml`. */
class LibraryTest {
    private val vcsRoot = GitVcsRoot {
        id("IsolatedSource")
        name = "Isolated source"
        url = "https://example.test/source.git"
        branch = "refs/heads/main"
    }
    private val repo = KtorRepository(vcsRoot, "Isolated_", "Isolated ")
    private val linuxEnvironment get() = repo.gradleEnvironments.first { it.os == Agents.OS.Linux }

    private fun BuildType.script(name: String): String = when (val step = steps.items.single { it.name == name }) {
        is ScriptBuildStep -> step.scriptContent!!
        is PowerShellStep -> (step.scriptMode as PowerShellStep.ScriptMode.Script).content!!
        else -> error("${step.name} is not a script step")
    }

    @Test
    fun `environments use the root, ID and URL of their repository`() {
        val environment = linuxEnvironment

        assertEquals("Isolated_GradleEnvironment_Linux", environment.id.toString())
        assertEquals("Isolated Gradle Environment (Linux)", environment.name)
        assertEquals(listOf(vcsRoot.id), environment.vcs.entries.map { it.root.id })
        val checkouts = environment.steps.items.filterIsInstance<ScriptBuildStep>()
            .filter { it.name.startsWith("Check out") }.map { it.scriptContent!! }
        assertTrue(checkouts.isNotEmpty())
        assertTrue(checkouts.all { "git fetch --depth=1 https://example.test/source.git" in it }, "$checkouts")
    }

    @Test
    fun `verification builds use environments of their repository`() {
        val builds = createCoreVerificationBuilds(repo)
        val coreEnvironments = Core.gradleEnvironments.map { it.id.toString() }

        fun BuildType.dependencyIds() = dependencies.items.map { it.buildTypeId.id.toString() }
        assertTrue("Isolated_GradleEnvironment_Linux" in builds.jvmCompileBuild.dependencyIds())
        assertTrue(builds.builds.isNotEmpty())
        for (build in builds.builds + builds.jvmCompileBuild) {
            val ids = build.dependencyIds()
            assertTrue(ids.none { it in coreEnvironments }, "${build.name}: $ids")
        }
    }

    @Test
    fun `publishing builds prompt for the release version by default`() {
        val version = PublishJvmToMavenBuild(repo).params.params.single { it.name == "releaseVersion" }

        assertEquals("", version.value)
        assertNotNull(version.spec)
    }

    @Test
    fun `publishing builds of a repository with version tags use the tag as the release version`() {
        val tagged = repo.copy(releaseVersionFromTag = true)
        val builds = listOf(PublishJvmToMavenBuild(tagged), PublishCustomTaskToMavenBuild(tagged))

        for (build in builds) {
            val version = build.params.params.single { it.name == "releaseVersion" }
            assertEquals("%teamcity.build.branch%", version.value, build.name)
            assertNull(version.spec, build.name)
        }
    }

    @Test
    fun `step conditions for the OS use a parameter that every agent reports`() {
        val conditions = PublishCustomTaskToMavenBuild(repo).steps.items.flatMap { it.conditions.items }

        assertTrue(conditions.isNotEmpty())
        assertEquals(setOf("teamcity.agent.jvm.os.name"), conditions.map { it.param }.toSet() - "prepublish_script")
    }

    @Test
    fun `files load from the classpath outside the settings directory`() {
        for (directory in listOf("scripts", "gradle.init.d", "gradle-runner")) {
            assertFalse(Path(directory).exists(), directory)
        }

        val script = requireNotNull(javaClass.getResource("/scripts/cancel_previous_builds.sh")).readText()
        assertEquals(script, JvmCompileBuild(repo).script("Cancel Previous Builds"))

        val userHome = linuxEnvironment.script("Reset Gradle user home")
        assertTrue("init.d/disable-cache-cleanup.init.gradle.kts" in userHome, userHome)
        assertTrue("gradle-runner-init.gradle" in userHome, userHome)
    }

    @Test
    fun `files in the working directory override classpath resources`() {
        val path = Path("scripts/cancel_previous_builds.sh")
        path.parent.createDirectories()
        try {
            path.writeText("echo local-script")

            assertEquals("echo local-script", JvmCompileBuild(repo).script("Cancel Previous Builds"))
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
    fun `signing credentials read the key from parameters of a parent project`() {
        val project = Project { params { signingCredentials() } }
        val values = project.params.params.associate { it.name to it.value }

        assertEquals("%sign.key.id%", values["env.SIGN_KEY_ID"])
        assertEquals("%sign.key.public%", values["env.SIGN_KEY_PUBLIC"])
        assertEquals("%sign.key.passphrase%", values["env.SIGN_KEY_PASSPHRASE"])
        assertEquals("%sign.key.private%", values["env.SIGN_KEY_PRIVATE"])
    }

    @Test
    fun `publishing credentials include signing and Maven Central credentials`() {
        val project = Project { params { publishingCredentials() } }
        val names = project.params.params.map { it.name }

        assertTrue("env.SIGN_KEY_PRIVATE" in names)
        assertTrue("env.ORG_GRADLE_PROJECT_mavenCentralUsername" in names)
        assertTrue("env.ORG_GRADLE_PROJECT_mavenCentralPassword" in names)
    }
}
