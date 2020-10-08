import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot
import org.junit.Test
import subprojects.*
import kotlin.test.assertTrue

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