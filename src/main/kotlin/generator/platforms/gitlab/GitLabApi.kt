package fanci.compiler.generator.platforms.gitlab

import fanci.compiler.common.VCSHelper

/**
 * Helper for emitting GitLab jobs that trigger a pipeline in another project
 * and fetch artifacts from a specific job.
 *
 * Simple initial version — parameters like target project, default branch, and API URL
 * are provided by the caller. Further automation can be added later.
 */
object GitLabApi {
    /**
     * Multi-job variant: trigger upstream pipeline once and download artifacts for all [neededBuildJobs].
     */
    fun createTriggerUpstreamDependencyJob(
        triggeredComponentName: String,
        targetProject: String,
        neededBuildJobs: List<String>,
        defaultBranch: String,
        startedByComponent: String,
        apiBaseUrl: String,
        tags: List<String>? = null,
        rules: List<Map<String, Any>>? = null,
        variablesFromEnv: List<Pair<String, String>>? = null
    ): Pair<String, GitLab.GitLabJob> {
        val jobName = "TriggerComponent$triggeredComponentName"

        val variables = mapOf(
            "TARGET_PROJECT" to targetProject, "DEFAULT_BRANCH" to defaultBranch, "CI_API_V4_URL" to apiBaseUrl
        )

        val script = buildTriggerAndFetchScriptMulti(neededBuildJobs, startedByComponent, variablesFromEnv)

        val job = GitLab.GitLabJob(
            stage = "default",
            image = "alpine:latest",
            tags = tags,
            variables = variables,
            beforeScript = null,
            script = script,
            afterScript = null,
            rules = rules,
            artifacts = mutableMapOf(
                "paths" to listOf("$artifactDirectory**/*"), "when" to "always"
            ),
            needs = null,
            trigger = null,
            parallel = null
        )

        return jobName to job
    }

    /**
     * Overload that derives CI_API_V4_URL and TARGET_PROJECT from a repository URL.
     * Useful when callers only know the upstream repository URL and default branch.
     */
    fun createTriggerUpstreamDependencyJobMultiFromRepo(
        triggeredComponentName: String,
        repositoryUrl: String,
        neededBuildJobs: List<String>,
        defaultBranch: String,
        startedByComponent: String,
        tags: List<String>? = null,
        rules: List<Map<String, Any>>? = null,
        variablesFromEnv: List<Pair<String, String>>? = null
    ): Pair<String, GitLab.GitLabJob> {
        val parsed = VCSHelper.parseApi(repositoryUrl)
        return createTriggerUpstreamDependencyJob(
            triggeredComponentName = triggeredComponentName,
            targetProject = parsed.targetProject,
            neededBuildJobs = neededBuildJobs,
            defaultBranch = defaultBranch,
            startedByComponent = startedByComponent,
            apiBaseUrl = parsed.apiBaseUrl,
            tags = tags,
            rules = rules,
            variablesFromEnv = variablesFromEnv
        )
    }

    /**
     * Script variant that fetches artifacts for multiple upstream job names.
     */
    private fun buildTriggerAndFetchScriptMulti(
        neededJobs: List<String>, startedByComponent: String, variablesFromEnv: List<Pair<String, String>>?
    ): List<String> {
        // Return a list where each element is a multi-line script block (so YAML emits "- |" per step)
        val steps = mutableListOf<String>()

        // Step 1: Install tools
        steps += """
            # 1. Install tools
            apk add --no-cache curl jq
        """.trimIndent()

        // Step 2: Encode target project path
        steps += """
            # 2. URL encode the target project path (e.g. group/project -> group%2Fproject)
            if [ -f build.env ]; then set -a; . build.env; set +a; fi
            # This allows us to query the API directly by path instead of searching by ID
            TARGET_PROJECT_ENCODED=$(echo -n "${'$'}TARGET_PROJECT" | jq -sRr @uri)
        """.trimIndent()

        // Step 3: Trigger downstream pipeline
        steps += """
            # 3. Trigger downstream pipeline on chosen branch
            BRANCH_TO_USE=$(echo -n "${'$'}CI_COMMIT_REF_NAME" | jq -sRr @uri)
            echo "project ID = ${'$'}TARGET_PROJECT_ENCODED Branch = ${'$'}BRANCH_TO_USE"
            PIPELINE_ID=$(curl --request POST \
                --form token=${'$'}CI_JOB_TOKEN \
                --form ref=${'$'}BRANCH_TO_USE \
                --form variables[STARTED_BY]="${startedByComponent}" \
                ${buildVariableForms(variablesFromEnv, "                ")} \
                "${'$'}CI_API_V4_URL/projects/${'$'}TARGET_PROJECT_ENCODED/trigger/pipeline" | jq -r '.id')
            if [ -z "${'$'}PIPELINE_ID" ] || [ "${'$'}PIPELINE_ID" == "null" ]; then
                echo "Pipeline failed to trigger in branch ${'$'}CI_COMMIT_REF_NAME Trying to trigger in default branch"
                BRANCH_TO_USE=$(echo -n "${'$'}DEFAULT_BRANCH" | jq -sRr @uri)
                PIPELINE_ID=$(curl --request POST \
                    --form token=${'$'}CI_JOB_TOKEN \
                    --form ref=${'$'}BRANCH_TO_USE \
                    --form variables[STARTED_BY]="${startedByComponent}" \
                    ${buildVariableForms(variablesFromEnv, "                    ")} \
                    "${'$'}CI_API_V4_URL/projects/${'$'}TARGET_PROJECT_ENCODED/trigger/pipeline" | jq -r '.id')
                if [ -z "${'$'}PIPELINE_ID" ] || [ "${'$'}PIPELINE_ID" == "null" ]; then
                    echo "Pipeline failed to trigger"
                    exit 1
                fi
            fi
            echo "Triggered pipeline: ${'$'}PIPELINE_ID"
        """.trimIndent()

        // Step 4: Poll until upstream finishes
        steps += """
            # 4. Poll until upstream finishes
            STATUS=""
            while [ "${'$'}STATUS" != "success" ] && [ "${'$'}STATUS" != "failed" ] && [ "${'$'}STATUS" != "canceled" ] && [ "${'$'}STATUS" != "skipped" ]; do
              sleep 10
              STATUS=$(curl -s --header "JOB-TOKEN: ${'$'}CI_JOB_TOKEN" "${'$'}CI_API_V4_URL/projects/${'$'}TARGET_PROJECT_ENCODED/pipelines/${'$'}PIPELINE_ID" | jq -r '.status')
              echo "Pipeline status: ${'$'}STATUS"
            done
            if [ "${'$'}STATUS" != "success" ]; then
              echo "Pipeline failed or was canceled or was skipped"
              exit 1
            fi
        """.trimIndent()

        // Steps 5..N: For each needed job, add a dedicated download step
        neededJobs.forEach { jobName ->
            val simpleName = jobName.split("__").last()
            steps += """
                # 5. Download artifacts for job: $simpleName
                ENCODED_BUILD_JOB=$(echo -n "$simpleName" | jq -sRr @uri)
                curl --header "JOB-TOKEN: ${'$'}CI_JOB_TOKEN" "${'$'}CI_API_V4_URL/projects/${'$'}TARGET_PROJECT_ENCODED/jobs/artifacts/${'$'}BRANCH_TO_USE/download?job=${'$'}ENCODED_BUILD_JOB" --output "artifacts-from$jobName.zip" || true
                tmpdir="$(mktemp -d)"
                unzip -o "artifacts-from$jobName.zip" -d "${'$'}tmpdir" || true
                mkdir -p $artifactDirectory$jobName/
                cp -a "${'$'}tmpdir/$artifactDirectory$simpleName/." "$artifactDirectory$jobName/" || true
                rm -rf "${'$'}tmpdir" || true
            """.trimIndent()
        }

        return steps
    }

    /**
     * Build lines with --form variables["NAME"]=${ENV} for curl call. Returns an empty string if none.
     *
     * Needs intent in spaces to format properly in YAML.
     */
    private fun buildVariableForms(vars: List<Pair<String, String>>?, indent: String): String {
        if (vars.isNullOrEmpty()) return ""
        return vars.joinToString("\\\n$indent") { (paramName, value) ->
            "--form variables[${paramName}]=\"${value}\""
        }.trimIndent()
    }
}
