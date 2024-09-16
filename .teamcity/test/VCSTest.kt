import jetbrains.buildServer.configs.kotlin.vcs.*
import org.junit.Test
import subprojects.*
import kotlin.test.*

class VCSTest {
    @Test
    fun vcsRootsHasAuthMethodWithPassword() {
        assertAuthWithPassword(VCSCore)
        assertAuthWithPassword(VCSDocs)
    }

    private fun assertAuthWithPassword(root: GitVcsRoot) {
        assertTrue(root.authMethod is GitVcsRoot.AuthMethod.Password, "${root.name} should have password auth method")
    }
}
