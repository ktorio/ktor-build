import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.projectFeatures.*
import subprojects.*
import subprojects.benchmarks.*
import subprojects.build.*
import subprojects.cli.*
import subprojects.eap.*
import subprojects.kotlinx.html.*
import subprojects.plugins.*
import subprojects.release.*
import subprojects.release.space.*

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/
version = "2025.07"

project {
    description = "Ktor Framework"

    vcsRoot(VCSCore)
    vcsRoot(VCSDocs)
    vcsRoot(VCSSamples)
    vcsRoot(VCSAPIDocs)
    vcsRoot(VCSCoreEAP)
    vcsRoot(VCSKotlinxHtml)
    vcsRoot(VCSKtorBenchmarks)
    vcsRoot(VCSKtorGeneratorWebsite)
    vcsRoot(VCSKtorCLI)
    vcsRoot(VCSKtorBuildPlugins)
    vcsRoot(VCSKtorBuildPluginsEAP)
    vcsRoot(VCSPluginRegistry)
    vcsRoot(VCSKtorGeneratorBackend)

    vcsRoot(VCSGetStartedSample)
    vcsRoot(VCSGradleSample)
    vcsRoot(VCSMavenSample)
    vcsRoot(VCSHttpApiSample)
    vcsRoot(VCSWebsiteSample)

    // DO NOT REMOVE
    params {
        param("teamcity.ui.settings.readOnly", "true")
    }

    // TODO: Create narrow group of approvers not to spam to averyone with notifications
    //features {
    //    untrustedBuildsSettings {
    //        id = "UntrustedBuildsPolicy"
    //        defaultAction = UntrustedBuildsSettings.DefaultAction.APPROVE
    //        approvalRules = "group:ALL_USERS_GROUP:1"
    //        manualRunsApproved = true
    //        enableLog = true
    //    }
    //}

    subProject(ProjectBuild)
    subProject(ProjectBenchmarks)
    subProject(ProjectRelease)
    subProject(ProjectPublishEAPToSpace)
    subProject(ProjectPublishReleaseToSpace)
    subProject(PublishKotlinxHtml)
    subProject(ProjectCLI)
    subProject(ProjectGradlePlugin)
}
