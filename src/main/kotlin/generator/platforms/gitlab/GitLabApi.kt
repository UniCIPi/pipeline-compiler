package com.bringauto.pipelinecompiler.generator.platforms.gitlab

/**
 * Helper for emitting GitLab jobs that trigger a pipeline in another project
 * and fetch artifacts from a specific job.
 *
 * Simple initial version — parameters like target project, default branch, and API URL
 * are provided by the caller. Further automation can be added later.
 */
object GitLabApi {
    /**
     * Extract host from URI authority string when java.net.URI.host is null (e.g., underscores in host).
     * Handles optional userinfo and port. Examples:
     *  - git@gitlab._vpn.example.com:1999 -> gitlab._vpn.example.com
     *  - gitlab._vpn.example.com:1999 -> gitlab._vpn.example.com
     *  - gitlab._vpn.example.com -> gitlab._vpn.example.com
     *  - user@gitlab._vpn.example.com -> gitlab._vpn.example.com
     */
    private fun parseHostFromAuthority(authority: String): String {
        val withoutUser = authority.substringAfter('@', authority)
        // Strip path part if any (unlikely in authority but be safe)
        val beforeSlash = withoutUser.substringBefore('/')
        // Then strip port
        return beforeSlash.substringBefore(':')
    }

    /**
     * Multi-job variant: trigger upstream pipeline once and download artifacts for all [neededBuildJobs].
     */
    fun createTriggerUpstreamDependencyJob(
        triggeredComponentName: String,
        targetProject: String,
        neededBuildJobs: List<String>,
        defaultBranch: String,
        startedByComponent: String,
        apiBaseUrl: String = "https://gitlab.fi.muni.cz/api/v4",
        tags: List<String>? = listOf("shared-fi"),
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
                "paths" to listOf("_artifacts/**/*"), "when" to "always"
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
        tags: List<String>? = listOf("shared-fi"),
        rules: List<Map<String, Any>>? = null,
        variablesFromEnv: List<Pair<String, String>>? = null
    ): Pair<String, GitLab.GitLabJob> {
        val parsed = parseGitLabRepository(repositoryUrl)
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
                mkdir -p _artifacts/$jobName/
                cp -a "${'$'}tmpdir/_artifacts/$simpleName/." "_artifacts/$jobName/" || true
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

    /**
     * Parse a GitLab repository URL and extract:
     *  - apiBaseUrl: <scheme>://<host>/api/v4 (or https://<host>/api/v4 for SSH URLs)
     *  - targetProject: group/subgroup/project (without .git)
     */
    fun parseGitLabRepository(repositoryUrl: String): ParsedGitLabRepo {
        val url = repositoryUrl.trim()
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            // HTTP(S) form: https://host/group/sub/project(.git)
            try {
                val uri = java.net.URI(url)
                val scheme = uri.scheme
                val rawAuthority = uri.rawAuthority ?: ""
                val hostFromUri = uri.host // may be null for RFC-invalid but commonly used hosts (e.g., underscores)
                val host = hostFromUri ?: parseHostFromAuthority(rawAuthority)
                // Path may start with '/'; strip and remove trailing .git
                val path = (uri.path ?: "").trim('/').removeSuffix(".git")
                ParsedGitLabRepo(
                    apiBaseUrl = "$scheme://$host/api/v4", targetProject = path
                )
            } catch (e: Exception) {
                // Fallback: best-effort parsing
                val cleaned = url.removePrefix("https://").removePrefix("http://")
                val firstSlash = cleaned.indexOf('/')
                val host = if (firstSlash >= 0) cleaned.substring(0, firstSlash) else cleaned
                val rest = if (firstSlash >= 0) cleaned.substring(firstSlash + 1) else ""
                ParsedGitLabRepo(
                    apiBaseUrl = "https://$host/api/v4", targetProject = rest.removeSuffix(".git")
                )
            }
        } else if (url.startsWith("ssh://") || url.startsWith("git+ssh://")) {
            // SSH URL form with scheme, optional user and port, e.g.:
            //  - ssh://git@gitlab.example.com:1999/group/sub/project.git
            //  - git+ssh://git@gitlab.example.com/group/sub/project.git
            try {
                val normalized = if (url.startsWith("git+ssh://")) url.replaceFirst("git+ssh://", "ssh://") else url
                val uri = java.net.URI(normalized)
                val rawAuthority = uri.rawAuthority ?: ""
                val hostFromUri = uri.host
                val host = hostFromUri ?: parseHostFromAuthority(rawAuthority)
                // Path may start with '/'; strip and remove trailing .git
                val path = (uri.path ?: "").trim('/').removeSuffix(".git")
                ParsedGitLabRepo(
                    // Intentionally map to HTTPS API base; SSH port is ignored for API
                    apiBaseUrl = "https://$host/api/v4", targetProject = path
                )
            } catch (e: Exception) {
                // Fallback to generic parsing below if URI fails
                val cleaned = url.removePrefix("ssh://").removePrefix("git+ssh://")
                val afterAt = cleaned.substringAfter('@', cleaned)
                val hostAndPath = afterAt
                val host = hostAndPath.substringBefore('/')
                val path = hostAndPath.substringAfter('/', "").removeSuffix(".git")
                ParsedGitLabRepo(
                    apiBaseUrl = "https://$host/api/v4", targetProject = path
                )
            }
        } else if (url.contains(":") && url.contains("@")) {
            // SSH form: git@host:group/sub/project(.git)
            val afterAt = url.substringAfter('@')
            val host = afterAt.substringBefore(':')
            val path = afterAt.substringAfter(':').removeSuffix(".git")
            ParsedGitLabRepo(
                apiBaseUrl = "https://$host/api/v4", targetProject = path
            )
        } else {
            // Unknown form; attempt generic split
            val cleaned = url.removeSuffix(".git")
            val parts = cleaned.split('/')
            val host = parts.getOrNull(0) ?: "gitlab.local"
            val path = parts.drop(1).joinToString("/")
            ParsedGitLabRepo(
                apiBaseUrl = "https://$host/api/v4", targetProject = path
            )
        }
    }

    data class ParsedGitLabRepo(val apiBaseUrl: String, val targetProject: String)
}
