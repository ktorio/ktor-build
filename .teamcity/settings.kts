import jetbrains.buildServer.configs.kotlin.*
import subprojects.*
import subprojects.benchmarks.*
import subprojects.build.*
import subprojects.build.samples.*
import subprojects.cli.*
import subprojects.eap.*
import subprojects.kotlinx.html.*
import subprojects.plugins.*
import subprojects.release.*
import subprojects.release.space.*
import subprojects.train.*

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
version = "2025.11"

project {
    description = "Ktor Framework"

    vcsRoot(VCSCore)
    vcsRoot(VCSDocs)
    vcsRoot(VCSSamples)
    vcsRoot(VCSKtorGeneratorWebsite)
    vcsRoot(VCSPluginRegistry)
    vcsRoot(VCSKtorGeneratorBackend)
    vcsRoot(VCSKtorBuildPlugins)
    vcsRoot(VCSKtorBuildPluginsEAP)

    // DO NOT REMOVE
    params {
//        param("teamcity.ui.settings.readOnly", "true")
        password("system.slack.webhook.url", value = "")

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
    subProject(ProjectBuildPluginSamples)
    subProject(ConsolidatedEAPValidation.createConsolidatedProject())
    subProject(ProjectPublishReleaseToSpace)
    subProject(PublishKotlinxHtml)
    subProject(ProjectCLI)
    subProject(ProjectGradlePlugin)
}
