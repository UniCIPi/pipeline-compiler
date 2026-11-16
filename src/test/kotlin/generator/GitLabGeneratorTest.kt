package fanci.compiler.generator

import fanci.compiler.InputParser
import fanci.compiler.generator.platforms.gitlab.GitLab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GitLabGeneratorTest {

    @Test
    fun `generate gitlab yaml for simple config`() {
        val input = InputParser()
        val cfgPath = java.io.File("src/test/resources/valid_pipeline_config.yaml")
        require(cfgPath.exists()) { "Test config missing: ${cfgPath.absolutePath}" }
        val settings = input.parseInputConfigFile(cfgPath)

        // Use test-only constructor to avoid requiring fully-qualified project paths in this unit test
        val dm = DependencyManager(allBuildConfigurations = listOf("::Project::Component::example_script"), isSingleConfiguration = true)
        dm.setCurrentComponent("::Project::Component")
        val platform = GitLab(dm, null)

        val tmp = Files.createTempDirectory("gitlab-gen-test")
        try {
            val resultPath = platform.generatePipeline(settings, tmp)
            val outFile = java.io.File(resultPath)
            assertTrue(outFile.exists(), ".gitlab-ci.yml should be generated")
            val text = outFile.readText()
            assertTrue(text.contains("stages"))
            // Expect the YAML to contain a job section (basic smoke test)
            assertTrue(text.contains("script:"))
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun `artifact patterns match the same paths as in TeamCity`() {
        val platform = GitLab(DependencyManager(allBuildConfigurations = emptyList(), isSingleConfiguration = true), null)
        val paths = listOf("a.txt", ".h.txt", "b.log", "d/a.txt", "d/e/a.txt", ".dotdir/a.txt", "d/.h/a.txt", "nested/x")
        val expected = mapOf(
            "a.txt" to listOf("a.txt"),
            "*.txt" to listOf("a.txt", ".h.txt"),
            "?.txt" to listOf("a.txt"),
            ".h.txt" to listOf(".h.txt"),
            ".*" to listOf(".h.txt"),
            "*" to listOf("a.txt", ".h.txt", "b.log"),
            "**" to paths,
            "**/a.txt" to listOf("a.txt", "d/a.txt", "d/e/a.txt", ".dotdir/a.txt", "d/.h/a.txt"),
            "d/**/a.txt" to listOf("d/a.txt", "d/e/a.txt", "d/.h/a.txt"),
            "*/a.txt" to listOf("d/a.txt", ".dotdir/a.txt"),
            "nest*/x" to listOf("nested/x"),
        )
        expected.forEach { (pattern, matched) ->
            val regex = Regex(platform.toArtifactRegex(pattern))
            assertEquals(matched, paths.filter { regex.matches(it) }, "pattern $pattern")
        }
    }
}
