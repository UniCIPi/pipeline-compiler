package fanci.compiler

import fanci.compiler.pipeline.buildConfiguration.ScriptRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.text.ParseException
import kotlin.io.path.createTempDirectory
import kotlin.test.assertFailsWith

class ResolveScriptFileTest {

    private fun writeConfigYaml(dir: File, projectName: String, scriptFileName: String): File {
        val yaml = """
            component:
              name: "$projectName"
              repository: "https://example.org/repo.git"
              default-branch: "main"
              submodule-strategy: "recursive"
              build-configurations:
                - type: ScriptRunner
                  configuration-name: "Run script"
                  steps:
                    - file: "$scriptFileName"
                      step-name: "Step"
        """.trimIndent()
        val cfg = File(dir, "pipeline-settings.yaml")
        cfg.writeText(yaml)
        return cfg
    }

    @Test
    fun `resolveScriptFile succeeds when script exists and is readable`() {
        val tempRoot = createTempDirectory("resolve-script-ok").toFile()
        val scripts = File(tempRoot, "scripts").apply { mkdirs() }
        val scriptName = "build.sh"
        val scriptFile = File(scripts, scriptName).apply { writeText("echo Hello") }
        val cfg = writeConfigYaml(tempRoot, "my-project", scriptName)

        val parser = InputParser()
        val settings = parser.parseInputConfigFile(cfg)

        // Ensure ScriptRunner picked up and inlined the script content
        val cfgs = settings.buildConfigurations.filterIsInstance<ScriptRunner>()
        assertEquals(1, cfgs.size, "There should be exactly one ScriptRunner config")
        val runner = cfgs.first()
        assertEquals(1, runner.buildSteps.size, "One script step should be inlined")
        assertEquals(scriptFile.readText(), runner.buildSteps.first().script)
    }

    @Test
    fun `resolveScriptFile throws when script file not found`() {
        val tempRoot = createTempDirectory("resolve-script-missing").toFile()
        // scripts dir exists but file is missing
        File(tempRoot, "scripts").apply { mkdirs() }
        val cfg = writeConfigYaml(tempRoot, "proj", "missing.sh")

        val parser = InputParser()
        val ex = assertFailsWith<IllegalArgumentException> {
            parser.parseInputConfigFile(cfg)
        }
        assertTrue(ex.message!!.contains("Script file not found or not readable"), ex.message)
    }

    @Test
    fun `resolveScriptFile rejects blank names`() {
        val tempRoot = createTempDirectory("resolve-script-blank").toFile()
        File(tempRoot, "scripts").apply { mkdirs() }
        val cfg = writeConfigYaml(tempRoot, "proj", "   ")

        val parser = InputParser()
        val ex = assertFailsWith<IllegalArgumentException> { parser.parseInputConfigFile(cfg) }
        assertTrue(ex.message!!.contains("name cannot be blank"), ex.message)
    }

    @Test
    fun `resolveScriptFile rejects absolute path`() {
        val tempRoot = createTempDirectory("resolve-script-abs").toFile()
        File(tempRoot, "scripts").apply { mkdirs() }
        val abs = File(tempRoot, "scripts/build.sh").absolutePath
        val cfg = writeConfigYaml(tempRoot, "proj", abs)

        val parser = InputParser()
        val ex = assertFailsWith<IllegalArgumentException> { parser.parseInputConfigFile(cfg) }
        assertTrue(ex.message!!.contains("absolute paths are not allowed"), ex.message)
    }

    @Test
    fun `resolveScriptFile rejects traversal`() {
        val tempRoot = createTempDirectory("resolve-script-traversal").toFile()
        File(tempRoot, "scripts").apply { mkdirs() }
        val cfg = writeConfigYaml(tempRoot, "proj", "../evil.sh")

        val parser = InputParser()
        val ex = assertFailsWith<IllegalArgumentException> { parser.parseInputConfigFile(cfg) }
        assertTrue(ex.message!!.contains("path traversal is not allowed"), ex.message)
    }

    @Test
    fun `resolveScriptFile rejects directory separators`() {
        val tempRoot = createTempDirectory("resolve-script-sep").toFile()
        File(tempRoot, "scripts").apply { mkdirs() }
        val cfg1 = writeConfigYaml(tempRoot, "proj", "sub/abc.sh")
        val parser = InputParser()
        val ex1 = assertFailsWith<IllegalArgumentException> { parser.parseInputConfigFile(cfg1) }
        assertTrue(ex1.message!!.contains("directory separators are not allowed"), ex1.message)
    }

    @Test
    fun `resolveScriptFile rejects unsupported characters`() {
        val tempRoot = createTempDirectory("resolve-script-chars").toFile()
        File(tempRoot, "scripts").apply { mkdirs() }
        val cfg = writeConfigYaml(tempRoot, "proj", "bad name.sh")

        val parser = InputParser()
        val ex = assertFailsWith<IllegalArgumentException> { parser.parseInputConfigFile(cfg) }
        assertTrue(ex.message!!.contains("contains unsupported characters"), ex.message)
    }

    private fun writeStepConfigYaml(dir: File, projectName: String, stepBody: String): File {
        val yaml = """
            component:
              name: "$projectName"
              repository: "https://example.org/repo.git"
              default-branch: "main"
              submodule-strategy: "recursive"
              build-configurations:
                - type: ScriptRunner
                  configuration-name: "Run script"
                  steps:
                    - step-name: "Step"
$stepBody
        """.trimIndent()
        val cfg = File(dir, "pipeline-settings.yaml")
        cfg.writeText(yaml)
        return cfg
    }

    @Test
    fun `script content defined in the step is used as the step script`() {
        val tempRoot = createTempDirectory("script-content-ok").toFile()
        val cfg = writeStepConfigYaml(tempRoot, "my-project", "                      script-content: \"echo Hello\"")

        val parser = InputParser()
        val settings = parser.parseInputConfigFile(cfg)

        val runner = settings.buildConfigurations.filterIsInstance<ScriptRunner>().single()
        assertEquals(1, runner.buildSteps.size, "One step should be created")
        assertEquals("echo Hello", runner.buildSteps.first().script)
    }

    @Test
    fun `multiline script content is preserved`() {
        val tempRoot = createTempDirectory("script-content-multiline").toFile()
        val stepBody = """
            |                      script-content: |
            |                        echo first
            |                        echo second
        """.trimMargin()
        val cfg = writeStepConfigYaml(tempRoot, "my-project", stepBody)

        val parser = InputParser()
        val settings = parser.parseInputConfigFile(cfg)

        val runner = settings.buildConfigurations.filterIsInstance<ScriptRunner>().single()
        assertEquals("echo first\necho second", runner.buildSteps.first().script)
    }

    @Test
    fun `step defining both file and script content is rejected`() {
        val tempRoot = createTempDirectory("script-content-both").toFile()
        File(tempRoot, "scripts").apply { mkdirs() }
            .let { File(it, "build.sh").writeText("echo Hello") }
        val stepBody = """
            |                      file: "build.sh"
            |                      script-content: "echo Hello"
        """.trimMargin()
        val cfg = writeStepConfigYaml(tempRoot, "my-project", stepBody)

        val parser = InputParser()
        val ex = assertFailsWith<ParseException> { parser.parseInputConfigFile(cfg) }
        assertTrue(ex.message!!.contains("mutually exclusive"), ex.message)
    }

    @Test
    fun `step defining neither file nor script content is rejected`() {
        val tempRoot = createTempDirectory("script-content-none").toFile()
        val cfg = writeStepConfigYaml(tempRoot, "my-project", "                      docker-arguments: \"-v /tmp:/tmp\"")

        val parser = InputParser()
        val ex = assertFailsWith<ParseException> { parser.parseInputConfigFile(cfg) }
        assertTrue(ex.message!!.contains("either 'file' or 'script-content' has to be defined"), ex.message)
    }
}
