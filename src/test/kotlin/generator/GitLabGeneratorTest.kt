package com.bringauto.pipelinecompiler.generator

import com.bringauto.pipelinecompiler.InputParser
import com.bringauto.pipelinecompiler.generator.platforms.gitlab.GitLab
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
}
