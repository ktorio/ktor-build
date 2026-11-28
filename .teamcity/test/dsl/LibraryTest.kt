package dsl

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.vcs.*
import org.junit.Test
import subprojects.*
import subprojects.build.core.*
import subprojects.build.gradle.*
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
    fun `protected builds use environments of the public repository`() {
        assertSame(Core.gradleEnvironments, Protected.gradleEnvironments)
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
    fun `Linux and macOS environments have no PowerShell step`() {
        for (environment in repo.gradleEnvironments.filter { it.os != Agents.OS.Windows }) {
            val powerShell = environment.steps.items.filterIsInstance<PowerShellStep>().map { it.name }
            assertEquals(emptyList(), powerShell, environment.name)
        }
    }

    @Test
    fun `Windows environment writes the user home files with any PowerShell edition`() {
        val environment = repo.gradleEnvironments.single { it.os == Agents.OS.Windows }
        val step = environment.steps.items.single { it.name == "Reset Gradle user home" } as PowerShellStep

        assertNull(step.edition)
    }

    /** Runs the shell step that resets the user home. Returns the exit code and the output. */
    private fun resetUserHome(userHome: String?): Pair<Int, String> {
        // TeamCity replaces the escaped symbol before it runs the script
        val script = linuxEnvironment.script("Reset Gradle user home").replace("%%", "%")
        val scriptFile = createTempFile(suffix = ".sh")
        try {
            scriptFile.writeText(script)
            val process = ProcessBuilder("bash", scriptFile.toString()).redirectErrorStream(true)
                .apply { environment().remove("GRADLE_USER_HOME") }
                .apply { if (userHome != null) environment()["GRADLE_USER_HOME"] = userHome }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            return process.waitFor() to output
        } finally {
            scriptFile.deleteIfExists()
        }
    }

    @Test
    fun `shell step removes only a directory with the name of the Gradle user home`() {
        val parent = createTempDirectory("reset-user-home")
        try {
            val kept = (parent / "kept.txt").apply { writeText("kept") }
            val relative = parent.relativeTo(Path("").toAbsolutePath()) / "gradle-user-home"
            val unsafe = listOf(null, "", "/", parent.toString(), "$parent/", "$parent/gradle-user-home-old", relative.toString())

            for (userHome in unsafe) {
                val (exitCode, output) = resetUserHome(userHome)
                assertNotEquals(0, exitCode, "GRADLE_USER_HOME=$userHome: $output")
                assertTrue("removes only" in output, "GRADLE_USER_HOME=$userHome: $output")
                assertEquals("kept", kept.readText())
                assertEquals(listOf("kept.txt"), parent.listDirectoryEntries().map { it.name })
            }
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `shell step replaces the Gradle user home with the files of the DSL`() {
        fun resource(path: String) = requireNotNull(javaClass.getResource(path)).readText().trimEnd() + "\n"
        val parent = createTempDirectory("reset-user-home")
        val home = (parent / "gradle-user-home").createDirectory()
        try {
            (home / "stale.txt").writeText("stale")
            val (exitCode, output) = resetUserHome(home.toString())
            assertEquals(0, exitCode, output)

            for (name in initScripts.keys) {
                assertEquals(resource("/gradle.init.d/$name.txt"), (home / "init.d" / name).readText(), name)
            }
            assertEquals(resource("/gradle-runner/init_v2.gradle"), (home / "gradle-runner-init.gradle").readText())
            assertTrue((home / "gradle.properties").readText().startsWith("org.gradle.java.installations.fromEnv="))
            assertEquals(initScripts.size + 2, home.toFile().walkTopDown().count { it.isFile })
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    /**
     * Runs the step that packs the user home, with only [tools] on the `PATH`.
     * Returns the directory that the step writes to, or `null` when a tool isn't installed.
     */
    private fun packUserHome(parent: java.nio.file.Path, tools: List<String>): java.nio.file.Path? {
        val home = (parent / "gradle-user-home").createDirectories()
        (home / "caches").createDirectories()
        (home / "caches" / "library.jar").writeText("library")
        (home / "daemon").createDirectories()
        (home / "daemon" / "daemon.log").writeText("log")
        java.nio.file.Files.createLink(home / "caches" / "linked.jar", home / "caches" / "library.jar")

        val bin = (parent / "bin").createDirectories()
        val path = System.getenv("PATH").split(java.io.File.pathSeparator).map { Path(it) }
        for (tool in tools) {
            val file = path.map { it / tool }.firstOrNull { it.isExecutable() } ?: return null
            java.nio.file.Files.createSymbolicLink(bin / tool, file)
        }
        val archives = (parent / "archives").createDirectories()
        val script = (parent / "pack.sh").apply {
            writeText(linuxEnvironment.script("Pack Gradle user home (Full)").replace("%system.teamcity.build.tempDir%", "$archives"))
        }
        val process = ProcessBuilder((bin / "bash").toString(), script.toString()).redirectErrorStream(true)
            .apply { environment().clear() }
            .apply { environment()["PATH"] = bin.toString() }
            .apply { environment()["GRADLE_USER_HOME"] = home.toString() }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
        return archives
    }

    private val packTools = listOf("bash", "sh", "find", "cp", "mv", "tar", "rm", "uname")

    @Test
    fun `agent without zstd leaves a copy of the user home for TeamCity to pack`() {
        val parent = createTempDirectory("pack-user-home")
        try {
            val archives = assertNotNull(packUserHome(parent, packTools))
            val copy = archives / "gradle-user-home-full.tar.zst.unpacked"

            assertEquals(listOf(copy.name), archives.listDirectoryEntries().map { it.name })
            assertEquals("library", (copy / "caches" / "library.jar").readText())
            assertEquals("library", (copy / "caches" / "linked.jar").readText())
            assertFalse((copy / "daemon").exists())

            val rules = linuxEnvironment.artifactRules.lines()
            assertTrue("+:%system.teamcity.build.tempDir%/${copy.name}/** => gradle-user-home-full.tar.zst" in rules, "$rules")
        } finally {
            parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `agent with zstd packs the user home itself`() {
        val parent = createTempDirectory("pack-user-home")
        try {
            // Null on a machine without zstd
            val archives = packUserHome(parent, packTools + "zstd") ?: return

            assertEquals(listOf("gradle-user-home-full.tar.zst"), archives.listDirectoryEntries().map { it.name })
            assertTrue((archives / "gradle-user-home-full.tar.zst").fileSize() > 0)
            assertTrue("%system.teamcity.build.tempDir%/gradle-user-home-full.tar.zst" in linuxEnvironment.artifactRules.lines())
        } finally {
            parent.toFile().deleteRecursively()
        }
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
