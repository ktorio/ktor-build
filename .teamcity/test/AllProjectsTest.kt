import jetbrains.buildServer.configs.kotlin.*
import org.junit.Test
import org.reflections.*
import org.reflections.scanners.*
import org.reflections.util.*
import subprojects.benchmarks.*
import subprojects.build.*
import subprojects.build.core.*
import subprojects.build.docsamples.*
import subprojects.build.generator.*
import subprojects.build.plugin.*
import subprojects.build.samples.*
import subprojects.cli.*
import subprojects.eap.*
import subprojects.kotlinx.html.*
import subprojects.plugins.*
import subprojects.release.*
import subprojects.release.apidocs.*
import subprojects.release.generator.*
import subprojects.release.publishing.*
import subprojects.release.space.*
import kotlin.test.*

class AllProjectsTest {
    @Test
    fun projectsHasDefinedIDs() {
        val projectsIDs = mapOf(
            ProjectBenchmarks to "ProjectKtorBenchmarks",
            ProjectCore to "ProjectKtorCore",
            ProjectDocSamples to "ProjectKtorDocs",
            ProjectGenerator to "ProjectKtorGenerator",
            ProjectPlugin to "ProjectKtorPlugin",
            ProjectSamples to "ProjectKtorSamples",
            ProjectBuild to "ProjectKtorBuild",
            ProjectRelease to "ProjectKtorRelease",
            ProjectCLI to "ProjectKtorCLI",
            ProjectGradlePlugin to "ProjectKtorGradlePlugin",
            PublishKotlinxHtml to "ProjectKotlinxHtml",

            ProjectReleaseAPIDocs to "ProjectKtorReleaseAPIDocs",
            ProjectReleaseGeneratorWebsite to "ProjectKtorReleaseGeneratorWebsite",
            ProjectPublishing to "ProjectKtorPublishing",
            ProjectPublishEAPToSpace to "ProjectKtorPublishEAPToSpace",
            ProjectPublishReleaseToSpace to "ProjectKtorPublishReleaseToSpace",
            CodeStyleVerify to "KtorCodeStyleVerifyKtLint"
        )

        allProjects().forEach { project ->
            assertTrue(project in projectsIDs, "Cannot find expected ID for project '${project.name}'")
            assertEquals(projectsIDs[project], project.id.toString(), "ID for Project '${project.name}' doesn't match")
            assertHasUniqueID(project)
        }
    }

    @Test
    fun projectsBuildTypesHasDefinedIDs() {
        val buildTypesIDs = mapOf(
            ProjectBenchmarks to setOf(
                "AllocationTests",
            ),

            //region Ktor
            ProjectCore to setOf(
                "KtorMatrixCoreLinuxJava8",
                "KtorMatrixCoreLinuxJava11",
                "KtorMatrixCoreLinuxJava17",
                "KtorMatrixCoreWindowsJava11",
                "KtorMatrixCoreMacOSJava8",
                "KtorMatrixCoreMacOSJava11",
                "KtorMatrixNativeMacOSX8664",
                "KtorMatrixNativeLinuxX64",
                "KtorMatrixJavaScriptChromeNodeJs",
                "KtorMatrixCoreAPICheck",
                "JPMSCheck",
                "KtorMatrixWasmJsChromeNodeJs",
                "KtorMatrixStressTestLinuxJava8",
                "KtorMatrixStressTestWindowsJava8",
                "KtorDependenciesCheckBuildId",
                "KtorCore_All",
                "KtorCodeStyleVerifyKtLint",
            ),

            ProjectDocSamples to setOf(
                "KtorDocs_ValidateSamples",
            ),

            ProjectGenerator to setOf(
                "KtorPluginRegistry",
                "KtorPluginRegistryVerify",
                "KtorGeneratorBackendVerify",
                "KtorGeneratorWebsite_Test",
            ),

            ProjectSamples to setOf(
                "KtorSamplesValidate_chat",
                "KtorSamplesValidate_client_mpp",
                "KtorSamplesValidate_client_multipart",
                "KtorSamplesValidate_client_tools",
                "KtorSamplesValidate_di_kodein",
                "KtorSamplesValidate_filelisting",
                "KtorSamplesValidate_fullstack_mpp",
                "KtorSamplesValidate_graalvm",
                "KtorSamplesValidate_httpbin",
                "KtorSamplesValidate_kweet",
                "KtorSamplesValidate_location_header",
                "KtorSamplesValidate_maven_google_appengine_standard",
                "KtorSamplesValidate_redirect_with_exception",
                "KtorSamplesValidate_reverse_proxy",
                "KtorSamplesValidate_reverse_proxy_ws",
                "KtorSamplesValidate_rx",
                "KtorSamplesValidate_sse",
                "KtorSamplesValidate_structured_logging",
                "KtorSamplesValidate_version_diff",
                "KtorSamplesValidate_youkube",
                "KtorSamplesValidate_websockets_chat_sample",
                "KtorSamplesValidate_All",
            ),
            //endregion

            ProjectCLI to setOf(
                "BuildCLI",
                "ReleaseGithubCLI",
                "PackMsiInstallerCLI",
            ),

            ProjectGradlePlugin to setOf(
                "PublishGradlePlugin",
                "PublishGradlePluginRelease",
                "PublishGradlePluginBeta",
                "PublishGradleEAPPlugin",
                "TestGradlePlugin",
            ),

            ProjectRelease to setOf(
                "KtorReleaseAllBuild",
            ),

            ProjectReleaseGeneratorWebsite to setOf(
                "KtorGeneratorWebsite_Deploy",
            ),

            ProjectReleaseAPIDocs to setOf(
                "KtorAPIDocs_Deploy",
            ),

            ProjectPublishing to setOf(
                "KtorPublishJvmToMavenBuild",
                "KtorPublishJSToMavenBuild",
                "KtorPublishWasmJsToMavenBuild",
                "KtorPublishWindowsNativeToMavenBuild",
                "KtorPublishLinuxNativeToMavenBuild",
                "KtorPublishMacOSNativeToMavenBuild",
                "KtorPublishCustomToMavenBuild",
                "KtorPublish_All",
            ),

            ProjectPublishEAPToSpace to setOf(
                "SetBuildNumberBuild",
                "KtorPublishCustomToSpaceBuild",
                "KtorPublishJvmToSpaceBuild",
                "KtorPublishJSToSpaceBuild",
                "KtorPublishWindowsNativeToSpaceBuild",
                "KtorPublishLinuxNativeToSpaceBuild",
                "KtorPublishMacOSNativeToSpaceBuild",
                "KtorPublish_AllEAP",
            ),

            ProjectPublishReleaseToSpace to setOf(
                "KtorPublishCustomToSpaceReleaseBuild",
                "KtorPublishJvmToSpaceReleaseBuild",
                "KtorPublishJSToSpaceReleaseBuild",
                "KtorPublishWindowsNativeToSpaceReleaseBuild",
                "KtorPublishLinuxNativeToSpaceReleaseBuild",
                "KtorPublishMacOSNativeToSpaceReleaseBuild",
                "KtorPublish_ReleaseToSpace",
            ),
        )

        allProjects().forEach { project ->
            if (project.buildTypes.size > 0) {
                assertTrue(
                    buildTypesIDs.containsKey(project),
                    "Cannot find build types IDs for project '${project.name}'"
                )
                val expectedIDs = buildTypesIDs.getValue(project)
                val ids = project.buildTypes.map { it.id.toString() }.toSet()

                val extraIDs = ids - expectedIDs
                val missingIDs = expectedIDs - ids
                val diffMessage = buildString {
                    if (missingIDs.isNotEmpty()) appendLine("Missing IDs: $missingIDs")
                    if (extraIDs.isNotEmpty()) appendLine("Extra IDs: $extraIDs")
                }

                assertEquals(
                    buildTypesIDs[project],
                    ids,
                    "Build types IDs for project '${project.name}' doesn't match.\n$diffMessage"
                )
            }
        }
    }

    @Test
    fun hasAtLeastOneProject() {
        assertTrue(allProjects().isNotEmpty(), "No projects found")
    }

    private fun assertHasUniqueID(project: Project) {
        val className = project.javaClass.name.split('.').last()
        assertNotEquals(
            className,
            project.id.toString(),
            "Project ID '${project.id.toString()}' should differ from its class name"
        )
    }

    private fun allProjects(): List<Project> {
        return projectsFromPackage("subprojects")
    }

    private fun projectsFromPackage(pkg: String): List<Project> {
        val reflections = Reflections(
            ConfigurationBuilder()
                .setScanners(Scanners.SubTypes.filterResultsBy { true }, Scanners.Resources)
                .setUrls(
                    ClasspathHelper.forClassLoader(
                        ClasspathHelper.contextClassLoader(),
                        ClasspathHelper.staticClassLoader()
                    )
                )
                .filterInputsBy(FilterBuilder().includePackage(pkg))
        )
        val projects = reflections.getSubTypesOf(Project::class.java).map { it.kotlin.objectInstance }

        if (projects.any { it == null }) {
            throw IllegalStateException("Found not object instanced projects")
        }

        return projects.filterNotNull()
    }
}
