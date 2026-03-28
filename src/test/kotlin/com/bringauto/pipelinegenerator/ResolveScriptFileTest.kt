package com.bringauto.pipelinecompiler

import com.bringauto.pipelinecompiler.pipeline.buildConfiguration.ScriptRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
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
              build:
                - type: ScriptRunner
                  configuration-name: "Run script"
                  scripts:
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
}
