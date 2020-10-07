import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import org.junit.Test
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import java.lang.IllegalStateException
import kotlin.test.assertNotEquals

class AllProjects {
    @Test
    fun testProjectsHasDefinedID() {
        projectsFromPackage("subprojects").forEach(::assertHasUniqueID)
    }

    private fun assertHasUniqueID(project: Project) {
        val className = project.javaClass.name.split('.').last()
        assertNotEquals(className, project.id.toString(), "Project ID '${project.id.toString()}' should differ from its class name")
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