package fanci.compiler.pipeline

import fanci.compiler.configurationModel.InputConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import fanci.compiler.pipeline.buildConfiguration.SemanticRelease
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SemanticRelease is an ordinary build type: it is listed in `build-configurations` and uses the common
 * fields of a build configuration.
 */
class SemanticReleaseParsingTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    private fun pipelineSettings(releaseFields: String, repository: String = "https://github.com/org/comp.git"): PipelineSettings {
        val yaml = """
            |component:
            |  name: "::comp"
            |  repository: "$repository"
            |  default-branch: trunk
            |  build-configurations:
            |  - type: ScriptRunner
            |    configuration-name: "Build"
            |    steps:
            |    - step-name: "s1"
            |      script-content: "make"
            |  - type: SemanticRelease
            |    preset: "BringAuto"
            |    repo-access-token-id: "release-token"
            |${releaseFields.trimIndent().prependIndent("    ")}
        """.trimMargin()
        val input: InputConfiguration = mapper.readValue(yaml)
        return PipelineSettings(input)
    }

    private fun release(releaseFields: String, repository: String = "https://github.com/org/comp.git"): SemanticRelease =
        assertIs<SemanticRelease>(pipelineSettings(releaseFields, repository).buildConfigurations.last())

    private val image = "docker-image: \"registry.example.com/semantic-release:25\""

    @Test
    fun `release is one of the build configurations of the component`() {
        val settings = pipelineSettings(image)
        assertEquals(listOf("Build", "Component release"), settings.buildConfigurations.map { it.name })
    }

    @Test
    fun `common fields keep their meaning`() {
        val configuration = release(
            """
            configuration-name: "Release"
            docker-image: "registry.example.com/semantic-release:25"
            docker-connections:
            - "registry"
            dependencies:
            - build-configuration-name: "Build"
              failure-action: FAIL
            """
        )
        assertEquals("Release", configuration.name)
        assertEquals("registry.example.com/semantic-release:25", configuration.dockerImage)
        assertEquals(listOf("registry"), configuration.dockerConnections)
        assertEquals(listOf("Build"), configuration.dependencies.map { it.buildConfigurationName })
        assertEquals(IBuildConfiguration.Type.DEPLOYMENT, configuration.type)
    }

    @Test
    fun `default trigger releases from the release branches`() {
        val configuration = release(
            """
            $image
            version-file-path: "version.txt"
            """
        )
        val trigger = assertIs<VcsTrigger>(configuration.triggers.single())
        assertEquals(listOf("trunk", "*.x"), trigger.includeBranches)
        assertEquals(listOf("version.txt", "CHANGELOG.md"), trigger.excludePaths)
        assertEquals(listOf("DRY_RUN" to "false"), trigger.parameters.map { it.name to it.value })
    }

    @Test
    fun `defined triggers replace the default trigger`() {
        val configuration = release(
            """
            $image
            triggers:
            - type: schedule
              cron: "0 3 * * *"
              include-branches:
              - trunk
            """
        )
        assertIs<ScheduleTrigger>(configuration.triggers.single())
    }

    @Test
    fun `release is a dry run unless a trigger changes it`() {
        val dryRun = release(image).parameters.single { it.name == "DRY_RUN" }
        assertEquals("true", dryRun.value)
    }

    @Test
    fun `semantic-release configuration follows the component`() {
        val configuration = release(image, repository = "https://gitlab.example.com/org/comp.git")
        val encoded = configuration.parameters.single { it.name == "RELEASE_RC_CONTENT" }.value
        val releaseRc = String(Base64.getDecoder().decode(encoded))
        assertTrue(releaseRc.contains("\"repositoryUrl\" : \"https://gitlab.example.com/org/comp.git\""))
        assertTrue(releaseRc.contains("\"name\" : \"trunk\""))
        assertTrue(releaseRc.contains("@semantic-release/gitlab"))
        assertEquals("GL_TOKEN", configuration.tokenName)
    }

    @Test
    fun `token is a secret parameter referencing the token id`() {
        val token = assertIs<SecretParameter>(release(image).parameters.single { it.name == "TOKEN" })
        assertEquals("release-token", token.value)
    }

    @Test
    fun `docker image is required`() {
        assertFails { release("configuration-name: \"Release\"") }
    }

    @Test
    fun `component has no release section`() {
        val yaml = """
            component:
              name: "::comp"
              repository: "https://github.com/org/comp.git"
              build-configurations: []
              release:
                preset: "BringAuto"
        """.trimIndent()
        assertFails { mapper.readValue<InputConfiguration>(yaml) }
    }
}
