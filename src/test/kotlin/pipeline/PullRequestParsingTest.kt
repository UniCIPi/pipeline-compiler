package fanci.compiler.pipeline

import fanci.compiler.common.VCSType
import fanci.compiler.configurationModel.InputConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PullRequestParsingTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    private fun component(pullRequestSection: String): String = """
        |component:
        |  name: "::Project::Component"
        |  repository: "https://gitlab.example.com/group/repository.git"
        |  submodule-strategy: none
        |$pullRequestSection
        |  build-configurations:
        |    - type: ScriptRunner
        |      configuration-name: "Build"
        |      steps:
        |        - step-name: "Step"
        |          file: "script.sh"
    """.trimMargin()

    @Test
    fun `parse pull request section with branch filters`() {
        val yaml = component(
            """
            |  pull-request:
            |    token-id: "credentialsJSON:pull-request-token"
            |    include-source-branches:
            |      - feature/*
            |    exclude-source-branches:
            |      - wip/*
            |    include-target-branches:
            |      - main
            |      - release/*
            |    exclude-target-branches:
            |      - legacy
            """.trimMargin()
        )

        val settings = PipelineSettings(mapper.readValue<InputConfiguration>(yaml))
        val configuration = settings.pullRequestConfiguration

        assertTrue(configuration.enabled)
        assertEquals("credentialsJSON:pull-request-token", configuration.tokenId)
        assertEquals(listOf("feature/*"), configuration.includeSourceBranches)
        assertEquals(listOf("wip/*"), configuration.excludeSourceBranches)
        assertEquals(listOf("main", "release/*"), configuration.includeTargetBranches)
        assertEquals(listOf("legacy"), configuration.excludeTargetBranches)
        assertTrue(!configuration.watchesAllPullRequests())
    }

    @Test
    fun `component without pull request section supports all pull requests`() {
        val settings = PipelineSettings(mapper.readValue<InputConfiguration>(component("")))
        val configuration = settings.pullRequestConfiguration

        assertTrue(configuration.enabled)
        assertNull(configuration.tokenId)
        assertTrue(configuration.watchesAllPullRequests())
    }

    @Test
    fun `pull request support can be disabled`() {
        val yaml = component(
            """
            |  pull-request:
            |    enabled: false
            """.trimMargin()
        )

        val settings = PipelineSettings(mapper.readValue<InputConfiguration>(yaml))
        assertTrue(!settings.pullRequestConfiguration.enabled)
    }

    @Test
    fun `blank pull request token should fail`() {
        val yaml = component(
            """
            |  pull-request:
            |    token-id: ""
            """.trimMargin()
        )

        val exception = assertFails { mapper.readValue<InputConfiguration>(yaml) }
        assertTrue(exception.message?.contains("token-id cannot be blank") ?: false)
    }

    @Test
    fun `vcs type is resolved from the repository url`() {
        val settings = PipelineSettings(mapper.readValue<InputConfiguration>(component("")))
        assertEquals(VCSType.GITLAB, settings.vcsType)
    }

    @Test
    fun `pull request branches are translated per vcs host system`() {
        assertEquals("merge-requests/*", PullRequestBranch.translate(PullRequestBranch.ALL, VCSType.GITLAB))
        assertEquals("pull/*", PullRequestBranch.translate(PullRequestBranch.ALL, VCSType.GITHUB))
        assertEquals("pull/*", PullRequestBranch.translate(PullRequestBranch.ALL, VCSType.GITEA))
        assertNull(PullRequestBranch.translate(PullRequestBranch.ALL, VCSType.UNKNOWN))

        // A concrete pull request keeps its selector
        assertEquals("merge-requests/42", PullRequestBranch.translate("mr/42", VCSType.GITLAB))
        assertEquals("42", PullRequestBranch.selectorOf("mr/42"))
    }

    @Test
    fun `patterns that are not pull requests are kept unchanged`() {
        assertTrue(!PullRequestBranch.isPullRequestPattern("tags/*"))
        assertEquals("tags/*", PullRequestBranch.translate("tags/*", VCSType.GITLAB))
        assertEquals("main", PullRequestBranch.translate("main", VCSType.UNKNOWN))
    }
}
