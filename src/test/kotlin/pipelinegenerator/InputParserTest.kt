package fanci.compiler

import fanci.compiler.configurationModel.SubmoduleStrategy
import fanci.compiler.pipeline.UploadedKey
import fanci.compiler.pipeline.buildConfiguration.ScriptRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.text.ParseException
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class InputParserTest {

    /**
     * Tests the parseInputConfigFile method from the InputParser class.
     * The method parses a given YAML configuration file and returns a PipelineSettings object.
     * The tests ensure proper handling of valid, invalid, and edge case scenarios.
     * Additional tests verify that valid configurations are correctly transformed into PipelineSettings.
     */

    @Test
    fun `submodule strategy defaults to none`() {
        val yaml = """
            component:
              name: "valid-project"
              repository: "https://github.com/example/repo.git"
              build-configurations:
                - type: GoTest
        """.trimIndent()

        val configFile = File.createTempFile("defaultSubmoduleStrategy", ".yaml")
        configFile.writeText(yaml)

        val result = InputParser().parseInputConfigFile(configFile)

        assertEquals(SubmoduleStrategy.none, result.submoduleStrategy)
    }

    @Test
    fun `test parseInputConfigFile with valid configuration file`() {
        val validYaml = """
            component:
              name: "valid-project"
              build-configurations:
                - type: GoTest
              repository: "https://github.com/example/repo.git"
              default-branch: "main"
              vcs-auth-method:
                type: uploaded-key
                name: key
              submodule-strategy: "recursive"
        """.trimIndent()

        val configFile = File.createTempFile("validConfig", ".yaml")
        configFile.writeText(validYaml)

        val inputParser = InputParser()
        val result = inputParser.parseInputConfigFile(configFile)

        assertNotNull(result)
        assertEquals("valid-project", result.componentName)
        assertEquals("https://github.com/example/repo.git", result.vcsRootUrl)
        assertEquals("main", result.defaultBranch)
        val vcsAuthMethod = result.vcsAuthMethod
        assertIs<UploadedKey>(vcsAuthMethod)
        assertEquals("key", vcsAuthMethod.name)
        assertEquals(SubmoduleStrategy.recursive, result.submoduleStrategy)
    }

    @Test
    fun `test parseInputConfigFile with invalid configuration file`() {
        val invalidYaml = """
            component:
              name: "missing-fields"
        """.trimIndent()

        val configFile = File.createTempFile("invalidConfig", ".yaml")
        configFile.writeText(invalidYaml)

        val inputParser = InputParser()
        val ex = assertFailsWith<ParseException> {
            inputParser.parseInputConfigFile(configFile)
        }
        assertTrue(ex.message!!.contains("Error parsing configuration file"))
    }

    @Test
    fun `test parseInputConfigFile with malformed YAML file`() {
        val malformedYaml = """
            component
              name: "malformed-file"
        """.trimIndent()

        val configFile = File.createTempFile("malformedConfig", ".yaml")
        configFile.writeText(malformedYaml)

        val inputParser = InputParser()
        val ex = assertFailsWith<ParseException> {
            inputParser.parseInputConfigFile(configFile)
        }
        assertTrue(ex.message!!.contains("Error parsing configuration file"))
    }

    @Test
    fun `test parseInputConfigFile transforms valid configuration with script to PipelineSettings`() {
        // Predefined valid configuration file path (example from configuration directory)
        val validConfigFilePath = "src/test/resources/valid_pipeline_config.yaml"
        val validConfigFile = File(validConfigFilePath)

        assertTrue(validConfigFile.exists(), "Valid configuration file does not exist!")

        val inputParser = InputParser()
        val pipelineSettings = inputParser.parseInputConfigFile(validConfigFile)

        assertNotNull(pipelineSettings, "PipelineSettings should not be null for a valid configuration!")
        assertEquals("::example-project", pipelineSettings.componentName)
        assertEquals("https://github.com/example/repository.git", pipelineSettings.vcsRootUrl)
        assertEquals("main", pipelineSettings.defaultBranch)
        assertEquals(SubmoduleStrategy.recursive, pipelineSettings.submoduleStrategy)
        assertTrue(pipelineSettings.buildConfigurations.isNotEmpty(), "Build configurations should not be empty!")

        // Additional assertions on build configurations, if needed
        val firstBuildConfig = pipelineSettings.buildConfigurations.first()
        assertTrue(firstBuildConfig is ScriptRunner, "First build configuration should be of type ScriptRunner.")
        assertEquals("example_script.sh", (firstBuildConfig as ScriptRunner).stepSpecifications.first().file)
        assertEquals("echo \"Hello from example_script.sh\"", firstBuildConfig.buildSteps.first().script)
    }
}