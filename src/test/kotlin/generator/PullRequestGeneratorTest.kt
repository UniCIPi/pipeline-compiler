package fanci.compiler.generator

import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.generator.platforms.gitlab.GitLab
import fanci.compiler.generator.platforms.teamcity.TeamCity
import fanci.compiler.pipeline.PipelineSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Generator level tests of the pull request support.
 * TeamCity is expected to configure the pull requests build feature, GitLab to configure rules only.
 */
class PullRequestGeneratorTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    private val componentName = "::Project::Component"

    private fun settings(
        repository: String = "https://gitlab.example.com/group/repository.git",
        vcsAuthMethod: String = """
            |  vcs-auth-method:
            |    type: token
            |    token-id: "credentialsJSON:vcs-token"
        """.trimMargin(),
        pullRequestSection: String = "",
        includeBranches: String = """
            |          include-branches:
            |            - main
            |            - "mr/*"
        """.trimMargin(),
        triggersSection: String = """
            |      triggers:
            |        - type: vcs
            |$includeBranches
        """.trimMargin()
    ): PipelineSettings {
        val yaml = """
            |component:
            |  name: "$componentName"
            |  repository: "$repository"
            |  submodule-strategy: none
            |$vcsAuthMethod
            |$pullRequestSection
            |  build-configurations:
            |    - type: ScriptRunner
            |      configuration-name: "Build"
            |      steps:
            |        - step-name: "Step"
            |          file: "script.sh"
            |$triggersSection
        """.trimMargin()
        return PipelineSettings(mapper.readValue<InputConfiguration>(yaml))
    }

    private fun dependencyManager(): DependencyManager {
        val manager = DependencyManager(
            allBuildConfigurations = listOf("$componentName::Build"),
            isSingleConfiguration = true
        )
        manager.setCurrentComponent(componentName)
        return manager
    }

    private fun generate(settings: PipelineSettings, platform: IPlatform): String {
        val directory = Files.createTempDirectory("pull-request-gen-test")
        try {
            platform.generatePipeline(settings, directory)
            return directory.toFile().walkTopDown()
                .filter { it.isFile }
                .joinToString("\n") { it.readText() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    // region TeamCity

    @Test
    fun `teamcity configures the pull requests feature for a gitlab component`() {
        val output = generate(settings(), TeamCity(dependencyManager(), null))

        assertTrue(output.contains("import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests"))
        assertTrue(output.contains("pullRequests {"))
        assertTrue(output.contains("provider = gitlab {"))
        // The component VCS root uses a token, so its credentials are reused
        assertTrue(output.contains("authType = vcsRoot()"))
        // Merge requests are exposed under merge-requests/<id> by the feature
        assertTrue(output.contains("+:merge-requests/*"))
    }

    @Test
    fun `teamcity uses the github provider and pull branches for a github component`() {
        val output = generate(
            settings(repository = "https://github.com/group/repository.git"),
            TeamCity(dependencyManager(), null)
        )

        assertTrue(output.contains("provider = github {"))
        assertTrue(output.contains("+:pull/*"))
        assertFalse(output.contains("merge-requests"))
    }

    @Test
    fun `teamcity uses the configured pull request token over the vcs root credentials`() {
        val output = generate(
            settings(
                pullRequestSection = """
                    |  pull-request:
                    |    token-id: "credentialsJSON:pull-request-token"
                """.trimMargin()
            ),
            TeamCity(dependencyManager(), null)
        )

        assertTrue(output.contains("authType = token {"))
        assertTrue(output.contains("token = \"credentialsJSON:pull-request-token\""))
    }

    @Test
    fun `teamcity translates branch filters to fully qualified references`() {
        val output = generate(
            settings(
                pullRequestSection = """
                    |  pull-request:
                    |    include-target-branches:
                    |      - main
                    |    exclude-source-branches:
                    |      - "wip/*"
                """.trimMargin()
            ),
            TeamCity(dependencyManager(), null)
        )

        assertTrue(output.contains("filterTargetBranch"))
        assertTrue(output.contains("+:refs/heads/main"))
        assertTrue(output.contains("filterSourceBranch"))
        assertTrue(output.contains("-:refs/heads/wip/*"))
    }

    @Test
    fun `teamcity omits the feature and the branches when pull requests are disabled`() {
        val output = generate(
            settings(
                pullRequestSection = """
                    |  pull-request:
                    |    enabled: false
                """.trimMargin()
            ),
            TeamCity(dependencyManager(), null)
        )

        assertFalse(output.contains("pullRequests {"))
        assertFalse(output.contains("merge-requests"))
        assertTrue(output.contains("branchFilter = \"+:main\""))
    }

    @Test
    fun `teamcity omits the feature for an unsupported vcs host system`() {
        val output = generate(
            settings(repository = "https://git.internal.example.org/group/repository.git"),
            TeamCity(dependencyManager(), null)
        )

        assertFalse(output.contains("pullRequests {"))
        assertTrue(output.contains("branchFilter = \"+:main\""))
    }

    // endregion

    // region GitLab

    @Test
    fun `gitlab creates a merge request rule and no build feature`() {
        val output = generate(settings(), GitLab(dependencyManager(), null))

        assertTrue(output.contains("\$CI_PIPELINE_SOURCE == 'merge_request_event'"))
        // GitLab needs rules only, merge request branches never appear in the pipeline definition
        assertFalse(output.contains("merge-requests"))
        assertFalse(output.contains("mr/"))
    }

    @Test
    fun `gitlab applies the component branch filters to the merge request rule`() {
        val output = generate(
            settings(
                pullRequestSection = """
                    |  pull-request:
                    |    include-target-branches:
                    |      - main
                    |    exclude-source-branches:
                    |      - "wip/*"
                """.trimMargin()
            ),
            GitLab(dependencyManager(), null)
        )

        assertTrue(output.contains("\$CI_MERGE_REQUEST_TARGET_BRANCH_NAME == 'main'"))
        assertTrue(output.contains("\$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME !~ /^(wip\\/.*)$/"))
    }

    @Test
    fun `gitlab narrows the rule down to a concrete merge request`() {
        val output = generate(
            settings(
                includeBranches = """
                    |          include-branches:
                    |            - "mr/42"
                """.trimMargin()
            ),
            GitLab(dependencyManager(), null)
        )

        assertTrue(output.contains("\$CI_MERGE_REQUEST_IID == '42'"))
    }

    @Test
    fun `gitlab covers merge requests for a trigger without branch filters`() {
        val output = generate(
            settings(includeBranches = ""),
            GitLab(dependencyManager(), null)
        )

        assertTrue(output.contains("\$CI_PIPELINE_SOURCE == 'push'"))
        assertTrue(output.contains("\$CI_PIPELINE_SOURCE == 'merge_request_event'"))
    }

    @Test
    fun `gitlab excludes merge requests when the trigger excludes them`() {
        val output = generate(
            settings(
                includeBranches = """
                    |          include-branches:
                    |            - main
                    |          exclude-branches:
                    |            - "mr/*"
                """.trimMargin()
            ),
            GitLab(dependencyManager(), null)
        )

        val mergeRequestRule = output.lines()
            .indexOfFirst { it.contains("\$CI_PIPELINE_SOURCE == 'merge_request_event'") }
        assertTrue(mergeRequestRule >= 0, "A merge request rule is expected")
        assertEquals("when: never", output.lines()[mergeRequestRule + 1].trim())
    }

    @Test
    fun `gitlab creates no merge request rule when pull requests are disabled`() {
        val output = generate(
            settings(
                pullRequestSection = """
                    |  pull-request:
                    |    enabled: false
                """.trimMargin(),
                includeBranches = ""
            ),
            GitLab(dependencyManager(), null)
        )

        assertTrue(output.contains("\$CI_PIPELINE_SOURCE == 'push'"))
        assertFalse(output.contains("merge_request_event"))
    }

    @Test
    fun `gitlab runs a build configuration without triggers only in pipelines started by hand`() {
        val output = generate(
            settings(triggersSection = ""),
            GitLab(dependencyManager(), null)
        )

        assertTrue(output.contains("\$CI_PIPELINE_SOURCE == 'web' || \$CI_PIPELINE_SOURCE == 'api'"))
        assertFalse(output.contains("\$CI_PIPELINE_SOURCE == 'push'"))
    }

    @Test
    fun `gitlab runs a build configuration with triggers by hand and in no other unmatched pipeline`() {
        val output = generate(settings(), GitLab(dependencyManager(), null))

        val lines = output.lines().map { it.trim() }
        val handStarted = lines.indexOf("- if: \$CI_PIPELINE_SOURCE == 'web' || \$CI_PIPELINE_SOURCE == 'api'")
        assertTrue(handStarted >= 0, "A rule for pipelines started by hand is expected")
        assertEquals("- when: never", lines[handStarted + 2])
        assertFalse(lines.contains("when: manual"))
    }

    // endregion
}
