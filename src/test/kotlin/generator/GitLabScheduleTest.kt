package fanci.compiler.generator

import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.generator.platforms.gitlab.GitLab
import fanci.compiler.pipeline.PipelineSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Generator level tests of the GitLab schedule triggers.
 * Every schedule trigger is expected to start only its own build configuration and its dependencies.
 */
class GitLabScheduleTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    private val componentName = "::Project::Component"

    private val nightly = """
        |        - type: schedule
        |          cron: "0 1 * * *"
        |          include-branches:
        |            - main
        |          parameters:
        |            - name: "MODE"
        |              value: "nightly"
    """.trimMargin()

    private val weekly = """
        |        - type: schedule
        |          name: weekly
        |          cron: "0 2 * * 0"
        |          include-branches:
        |            - "release/*"
        |          parameters:
        |            - name: "MODE"
        |              value: "weekly"
    """.trimMargin()

    private val monthly = """
        |        - type: schedule
        |          cron: "0 3 1 * *"
    """.trimMargin()

    private fun generate(buildTriggers: List<String> = listOf(nightly, weekly, monthly)): String {
        val yaml = """
            |component:
            |  name: "$componentName"
            |  repository: "https://gitlab.example.com/group/repository.git"
            |  submodule-strategy: none
            |  build-configurations:
            |    - type: ScriptRunner
            |      configuration-name: "Build"
            |      steps:
            |        - step-name: "Step"
            |          file: "script.sh"
            |      dependencies:
            |        - build-configuration-name: "Prepare"
            |          failure-action: FAIL
            |      triggers:
            |${buildTriggers.joinToString("\n")}
            |    - type: ScriptRunner
            |      configuration-name: "Prepare"
            |      steps:
            |        - step-name: "Step"
            |          file: "script.sh"
            |    - type: ScriptRunner
            |      configuration-name: "Other"
            |      steps:
            |        - step-name: "Step"
            |          file: "script.sh"
            |      triggers:
            |        - type: schedule
            |          cron: "0 4 * * *"
        """.trimMargin()
        val settings = PipelineSettings(mapper.readValue<InputConfiguration>(yaml))
        val manager = DependencyManager(
            allBuildConfigurations = listOf("$componentName::Build", "$componentName::Prepare", "$componentName::Other"),
            isSingleConfiguration = true
        )
        manager.setCurrentComponent(componentName)

        val directory = Files.createTempDirectory("gitlab-schedule-test")
        try {
            GitLab(manager, null).generatePipeline(settings, directory)
            return directory.toFile().walkTopDown()
                .filter { it.isFile }
                .joinToString("\n") { it.readText() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    /** Rule conditions of the job [jobName], in the order of the rules. */
    private fun ruleConditions(output: String, jobName: String): List<String> {
        val lines = output.lines()
        val jobStart = lines.indexOf("$jobName:")
        assertTrue(jobStart >= 0, "Job $jobName is expected")
        return lines.drop(jobStart + 1)
            .takeWhile { it.isEmpty() || it.startsWith(" ") || it.startsWith("-") }
            .map { it.trim() }
            .filter { it.startsWith("- if: ") }
            .map { it.removePrefix("- if: ") }
    }

    private fun scheduleCondition(scheduleId: String) =
        "\$CI_PIPELINE_SOURCE == 'schedule' && \$__PIPELINE_GENERATOR_SCHEDULE == '$scheduleId'"

    /** Messages of the exception and all its causes, the exception may be wrapped by the YAML parser. */
    private fun messages(exception: Throwable): String =
        generateSequence(exception) { it.cause }.mapNotNull { it.message }.joinToString("\n")

    @Test
    fun `gitlab identifies every schedule trigger by the schedule variable`() {
        val conditions = ruleConditions(generate(), "Build")

        assertTrue(conditions.contains(scheduleCondition("Build-1")))
        assertTrue(conditions.contains(scheduleCondition("Build-weekly")))
        assertTrue(conditions.contains(scheduleCondition("Build-2")))
        assertFalse(conditions.contains("\$CI_PIPELINE_SOURCE == 'schedule'"))
    }

    @Test
    fun `gitlab keeps the identifiers of the schedule triggers when a named trigger is moved`() {
        val conditions = ruleConditions(generate(listOf(weekly, nightly, monthly)), "Build")

        assertTrue(conditions.contains(scheduleCondition("Build-weekly")))
        assertTrue(conditions.contains(scheduleCondition("Build-1")))
        assertTrue(conditions.contains(scheduleCondition("Build-2")))
        assertFalse(conditions.contains(scheduleCondition("Build-3")))
    }

    @Test
    fun `gitlab sets the parameters of the schedule which started the pipeline`() {
        val lines = generate().lines().map { it.trim() }

        val nightlyRule = lines.indexOfFirst { it.contains("== 'Build-1'") }
        val weeklyRule = lines.indexOfFirst { it.contains("== 'Build-weekly'") }
        assertTrue(lines.subList(nightlyRule, weeklyRule).any { it.endsWith(": nightly") })
        assertFalse(lines.subList(nightlyRule, weeklyRule).any { it.endsWith(": weekly") })
    }

    @Test
    fun `gitlab starts the dependencies by the schedule of the dependent`() {
        val conditions = ruleConditions(generate(), "Prepare")

        assertTrue(conditions.contains(scheduleCondition("Build-1")))
        assertTrue(conditions.contains(scheduleCondition("Build-weekly")))
        assertFalse(conditions.any { it.contains("'Other-1'") })
    }

    @Test
    fun `gitlab omits a vcs trigger watching another component`() {
        val watchingLibrary = """
            |        - type: vcs
            |          watched-component: "::Project::Library"
            |          include-branches:
            |            - main
        """.trimMargin()
        val conditions = ruleConditions(generate(listOf(watchingLibrary, nightly)), "Build")

        assertFalse(conditions.any { it.contains("'push'") })
        assertTrue(conditions.contains(scheduleCondition("Build-1")))
    }

    @Test
    fun `gitlab keeps a vcs trigger watching the owning component`() {
        val watchingItself = """
            |        - type: vcs
            |          watched-component: "$componentName"
            |          include-branches:
            |            - main
        """.trimMargin()
        val conditions = ruleConditions(generate(listOf(watchingItself)), "Build")

        assertTrue(conditions.contains("\$CI_PIPELINE_SOURCE == 'push' && \$CI_COMMIT_BRANCH == 'main'"))
    }

    @Test
    fun `gitlab leaves the branch of a schedule to the schedule itself`() {
        val conditions = ruleConditions(generate(), "Build")

        assertFalse(conditions.any { it.contains("\$CI_COMMIT_BRANCH") })
    }

    @Test
    fun `gitlab rejects a schedule trigger name colliding with the numbering`() {
        val namedOne = """
            |        - type: schedule
            |          name: "1"
            |          cron: "0 5 * * *"
        """.trimMargin()

        val exception = assertFails { generate(listOf(nightly, namedOne)) }
        assertTrue(messages(exception).contains("share the identifier 'Build-1'"))
    }

    @Test
    fun `schedule trigger names must be unique within a build configuration`() {
        val exception = assertFails { generate(listOf(weekly, weekly)) }
        assertTrue(messages(exception).contains("Schedule trigger names must be unique within a build configuration: weekly"))
    }
}
