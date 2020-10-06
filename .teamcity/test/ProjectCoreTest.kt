import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import org.junit.Test
import subprojects.build.core.ProjectCore
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProjectCoreTest {
    @Test
    fun testProjectHasDefinedID() {
        assertHasUniqueID("ProjectKtorCore", ProjectCore)
    }

    private fun assertHasUniqueID(expectedID: String, project: Project) {
        val className = project.javaClass.name.split('.').last()
        assertNotEquals(className, ProjectCore.id.toString(), "ID '${ProjectCore.id.toString()}' should differ from its class name")
        assertEquals(expectedID, project.id.toString())
    }
}