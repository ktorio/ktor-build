import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import org.junit.Test
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import subprojects.build.ProjectBuild
import subprojects.build.core.ProjectCore
import subprojects.build.docsamples.ProjectDocSamples
import subprojects.build.generator.ProjectGenerator
import subprojects.build.plugin.ProjectPlugin
import subprojects.build.samples.ProjectSamples
import subprojects.release.ProjectRelease
import java.lang.IllegalStateException
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AllProjectsTest {
    @Test
    fun projectsHasDefinedIDs() {
        val projectsIDs = mapOf(
                ProjectCore to "ProjectKtorCore",
                ProjectDocSamples to "ProjectKtorDocs",
                ProjectGenerator to "ProjectKtorGenerator",
                ProjectPlugin to "ProjectKtorPlugin",
                ProjectSamples to "ProjectKtorSamples",
                ProjectBuild to "ProjectKtorBuild",
                ProjectRelease to "ProjectKtorRelease"
        )

        allProjects().forEach { project ->
            assertTrue(projectsIDs.containsKey(project), "Cannot find expected ID for project ${project.name}")
            assertEquals(projectsIDs[project], project.id.toString(), "ID for Project ${project.name} doesn't match")
            assertHasUniqueID(project)
        }
    }

    @Test
    fun projectsBuildTypesHadDefinedIDs() {
        val buildTypesIDs = mapOf(
                ProjectCore to setOf(
                        "KtorMatrixMacOSJava8",
                        "KtorMatrixMacOSJava11",
                        "KtorMatrixLinuxJava8",
                        "KtorMatrixLinuxJava11",
                        "KtorMatrixWindowsJava8",
                        "KtorMatrixWindowsJava11",
                        "KtorMatrixNativeMacOS",
                        "KtorMatrixNativeLinux",
                        "KtorMatrixNativeWindows",
                        "KtorMatrixJavaScriptChrome"

                ),
                ProjectDocSamples to setOf("KtorDocs_ValidateSamples")
        )

        allProjects().forEach { project ->
            if (project.buildTypes.size > 0) {
                assertTrue(buildTypesIDs.containsKey(project), "Cannot find build types IDs for project ${project.name}")
                val ids = project.buildTypes.map { it.id.toString() }.toSet()
                assertEquals(buildTypesIDs[project], ids, "Build types IDs for project ${project.name} doesn't match")
            }
        }
    }

    @Test
    fun hasAtLeastOneProject() {
        assertTrue(allProjects().isNotEmpty(), "No projects found")
    }

    private fun assertHasUniqueID(project: Project) {
        val className = project.javaClass.name.split('.').last()
        assertNotEquals(className, project.id.toString(), "Project ID '${project.id.toString()}' should differ from its class name")
    }

    private fun allProjects(): List<Project> {
        return projectsFromPackage("subprojects")
    }

    private fun projectsFromPackage(pkg: String): List<Project> {
        val reflections = Reflections(ConfigurationBuilder()
                .setScanners(SubTypesScanner(false), ResourcesScanner())
                .setUrls(ClasspathHelper.forClassLoader(ClasspathHelper.contextClassLoader(), ClasspathHelper.staticClassLoader()))
                .filterInputsBy(FilterBuilder().include(FilterBuilder.prefix(pkg))))
        val projects = reflections.getSubTypesOf(Project::class.java).map { it.kotlin.objectInstance }

        if (projects.any { it == null }) {
            throw IllegalStateException("Found not object instanced projects")
        }

        return projects.filterNotNull()
    }
}