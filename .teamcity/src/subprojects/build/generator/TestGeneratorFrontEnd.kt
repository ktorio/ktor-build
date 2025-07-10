package subprojects.build.generator

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.*
import jetbrains.buildServer.configs.kotlin.triggers.*
import subprojects.*
import subprojects.build.*

object TestGeneratorFrontEnd : BuildType({
    id("KtorGeneratorFrontendVerify")
    name = "Test generator frontend"
    params {
        password("env.SPACE_USERNAME", value = "%space.packages.apl.user%")
        password("env.SPACE_PASSWORD", value = "%space.packages.apl.token%")
        param("env.DOCKER_COMPOSE_VERSION", "2.24.5")
        param("env.NODE_VERSION", "18")
        param("env.JAVA_VERSION", "17")
        param("env.JAVA_DISTRIBUTION", "temurin")
    }

    vcs {
        root(VCSKtorGeneratorWebsite)
    }

    steps {
        script {
            name = "Setup Docker and Registry Login"
            scriptContent = """
                # Set up Docker Buildx
                docker buildx create --use
                
                # Login to Private Registry
                if [ -n "${'$'}env.SPACE_USERNAME" ] && [ -n "${'$'}env.SPACE_PASSWORD" ]; then
                  echo "${'$'}env.SPACE_PASSWORD" | docker login registry.jetbrains.team -u "${'$'}env.SPACE_USERNAME" --password-stdin
                else
                  echo "Error: Registry credentials not set"
                  exit 1
                fi
            """.trimIndent()
        }

        script {
            name = "Install Docker Compose"
            scriptContent = """
                sudo curl -L "https://github.com/docker/compose/releases/download/v${'$'}{env.DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                sudo chmod +x /usr/local/bin/docker-compose
                docker-compose --version
            """.trimIndent()
        }

        script {
            name = "Setup Node.js and Install Dependencies"
            scriptContent = """
                # Install Node.js
                export NVM_DIR="${'$'}HOME/.nvm"
                [ -s "${'$'}NVM_DIR/nvm.sh" ] && \. "${'$'}NVM_DIR/nvm.sh"
                nvm install ${'$'}{env.NODE_VERSION}
                nvm use ${'$'}{env.NODE_VERSION}
                
                # Install dependencies
                npm install
                npm ci
            """.trimIndent()
        }

        script {
            name = "Install Playwright browsers"
            scriptContent = "npx playwright install --with-deps"
        }

        script {
            name = "Build frontend"
            scriptContent = "npm run build"
        }

        script {
            name = "Run E2E tests"
            scriptContent = """
                chmod +x ./run-e2e-tests.sh
                ./run-e2e-tests.sh
            """.trimIndent()
        }
    }

    defaultBuildFeatures(VCSKtorGeneratorWebsite.id.toString())

    artifactRules = """
    playwright-report/ => playwright-report.zip
    test-results/ => test-results.zip
    """.trimIndent()

    triggers {
        vcs {
            branchFilter = "+:refs/heads/(main|master)" +
                "+:refs/pull/*"
        }
    }
})
