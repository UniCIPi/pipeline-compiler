package fanci.compiler.generator

import fanci.compiler.common.ProjectPath
import fanci.compiler.pipeline.PipelineSettings

class DependencyManager {
    private val allBuildConfigurations: List<ProjectPath>
    private val isSingleConfiguration: Boolean
    /** Map component (project) -> PipelineSettings for repository/branch lookups */
    private val pipelineSettingsByProject: Map<ProjectPath, PipelineSettings>
    /**
     * Directed acyclic graph (expected) of build configuration dependencies.
     * Edge: A -> B means A depends on B (A requires B to complete first).
     */
    private val dependencyGraph: Map<ProjectPath, Set<ProjectPath>>

    /**
     * Current project name. Serves for relative dependency resolution.
     * This should point to the project path (without build type), e.g., ::Root::Project
     */
    var currentComponent: ProjectPath = ProjectPath.of()

    fun setCurrentComponent(componentName: String) {
        currentComponent = ProjectPath.parse(componentName)
    }

    constructor(pipelineSettingsList: List<PipelineSettings>) {
        allBuildConfigurations = getAllBuildConfigurations(pipelineSettingsList)
        isSingleConfiguration = pipelineSettingsList.size == 1

        // Map projects to their PipelineSettings
        pipelineSettingsByProject = pipelineSettingsList.associateBy { ProjectPath.parse(it.componentName) }

        // Build global dependency graph across all components
        dependencyGraph = buildDependencyGraph(pipelineSettingsList)
        // Validate no cycles exist
        detectCyclesOrThrow()
    }

    fun getAllBuildConfigurations(
        pipelineSettingsList: List<PipelineSettings>
    ): List<ProjectPath> {
        return pipelineSettingsList.flatMap { pipelineSettings ->
            pipelineSettings.buildConfigurations.map { buildConfiguration ->
                // pipelineSettings.projectName is full project path (e.g., ::Root::Project)
                // Append build name to get full build configuration path: ::Root::Project::Build
                val base = ProjectPath.parse(pipelineSettings.componentName)
                base.append(buildConfiguration.name)
            }
        }
    }

    fun findDependency(dependencyName: String): String {
        // If it's already a full path, either return as-is in single config mode,
        // or validate its existence.
        if (ProjectPath.isFullPath(dependencyName)) {
            if (isSingleConfiguration) {
                return dependencyName
            }
            val target = ProjectPath.parse(dependencyName)
            val exists = allBuildConfigurations.any { it == target }
            if (exists) return target.toString()

            handleNotFoundDependency(currentComponent.toString(), dependencyName)
            error("Unreachable")
        }

        // Relative build name: resolve against current project
        val target = currentComponent.append(dependencyName)
        val match = allBuildConfigurations.firstOrNull { it == target }
        if (match != null) return match.toString()

        handleNotFoundDependency(currentComponent.toString(), dependencyName)
        error("Unreachable")
    }

    private fun handleNotFoundDependency(currentProject: String, dependencyName: String) {
        if (!ProjectPath.isFullPath(dependencyName)) {
            throw IllegalArgumentException(
                "Build configuration '$dependencyName' not found in project '$currentProject'"
            )
        } else {
            val full = ProjectPath.parse(dependencyName)
            val projectName = ProjectPath.projectOf(full).toString()
            val buildName = ProjectPath.buildTypeOf(full)

            val anyProjectMatch = allBuildConfigurations.any { it.toString().startsWith("$projectName${ProjectPath.PROJECT_PATH_DELIMITER}") }
            if (!anyProjectMatch) {
                throw IllegalArgumentException("Project '$projectName' does not exist in available configurations")
            }
            throw IllegalArgumentException("Build configuration '$buildName' not found in project '$projectName'")
        }
    }

    fun getBuildType(path: String): String = ProjectPath.buildTypeOf(ProjectPath.parse(path))

    fun getProject(path: String): String = ProjectPath.projectOf(ProjectPath.parse(path)).toString()

    data class RepoInfo(val repositoryUrl: String, val defaultBranch: String)

    /**
     * Find repository URL and default branch for a dependency build configuration name.
     * The [dependencyName] can be a fully qualified build configuration name (preferred),
     * or a relative build name which will be resolved against [currentComponent].
     */
    fun getRepositoryOf(dependencyName: String): RepoInfo {
        val full = resolveAgainstCurrentComponent(dependencyName)
        // Full path: ::A::B::Component::Build -> we need ::A::B::Component
        val projectPath = ProjectPath.projectOf(full)
        val ps = pipelineSettingsByProject[projectPath]
            ?: throw IllegalArgumentException("Project '${projectPath}' does not exist in available configurations")
        return RepoInfo(repositoryUrl = ps.vcsRootUrl, defaultBranch = ps.defaultBranch)
    }

    /**
     * Returns true if [otherBuild] (directly or transitively) depends on [currentBuild].
     * Both arguments can be fully qualified (e.g., ::Root::Project::Build) or relative build names.
     * Relative names are resolved against [currentComponent].
     */
    fun isDependencyOf(currentBuild: String, otherBuild: String): Boolean {
        val target = resolveAgainstCurrentComponent(currentBuild)
        val source = resolveAgainstCurrentComponent(otherBuild)

        // Validate both exist
        if (!allBuildConfigurations.contains(target)) {
            handleNotFoundDependency(currentComponent.toString(), currentBuild)
        }
        if (!allBuildConfigurations.contains(source)) {
            handleNotFoundDependency(currentComponent.toString(), otherBuild)
        }

        if (target == source) return false // a build is not a dependency of itself by definition

        return isReachable(source, target)
    }

    /**
     * Returns a set of external component names (projects) that depend (directly or transitively)
     * on the given [targetBuild]. The [targetBuild] can be a fully qualified build name
     * (e.g. ::Group::Component::Build) or a local build name which will be resolved against
     * the current component.
     * Only components different from the target's component are returned.
     */
    fun getExternalDependentsOf(targetBuild: String): Set<String> {
        val target = resolveAgainstCurrentComponent(targetBuild)
        val targetProject = ProjectPath.projectOf(target)
        val result = mutableSetOf<String>()

        // Iterate all known builds and test reachability to the target
        allBuildConfigurations.forEach { source ->
            if (ProjectPath.projectOf(source) != targetProject) {
                if (isReachable(source, target)) {
                    result += ProjectPath.projectOf(source).toString()
                }
            }
        }
        return result
    }

    // region DAG construction and validation
    private fun buildDependencyGraph(pipelineSettingsList: List<PipelineSettings>): Map<ProjectPath, Set<ProjectPath>> {
        val nodes = allBuildConfigurations.toSet()
        val edges = mutableMapOf<ProjectPath, MutableSet<ProjectPath>>()
        // Initialize all nodes with empty dependency sets to keep isolated nodes
        nodes.forEach { edges[it] = mutableSetOf() }

        pipelineSettingsList.forEach { ps ->
            val base = ProjectPath.parse(ps.componentName)
            ps.buildConfigurations.forEach { bc ->
                val from = base.append(bc.name)
                // Only consider nodes known in allBuildConfigurations list
                if (!nodes.contains(from)) return@forEach
                bc.dependencies.forEach { dep ->
                    val depName = dep.buildConfigurationName
                    val to = if (ProjectPath.isFullPath(depName)) ProjectPath.parse(depName) else base.append(depName)

                    if (!nodes.contains(to)) {
                        // Report using the project's base for clarity
                        handleNotFoundDependency(base.toString(), depName)
                    }
                    require(from != to) {
                        "Build configuration '$from' cannot depend on itself"
                    }
                    edges.getValue(from).add(to)
                }
            }
        }

        // Convert to immutable sets
        return edges.mapValues { it.value.toSet() }
    }

    private fun detectCyclesOrThrow() {
        val nodes = dependencyGraph.keys.toList()
        val indexOf = nodes.withIndex().associate { it.value to it.index }
        val graph = Array(nodes.size) { mutableListOf<Int>() }
        dependencyGraph.forEach { (from, deps) ->
            val u = indexOf[from] ?: return@forEach
            deps.forEach { d -> indexOf[d]?.let { v -> graph[u].add(v) } }
        }

        val color = IntArray(nodes.size) { 0 } // 0=white,1=gray,2=black
        val parent = IntArray(nodes.size) { -1 }

        fun buildCyclePath(start: Int, end: Int): String {
            // start is a node being visited again from end
            val path = ArrayList<Int>()
            var v = end
            path.add(start)
            while (v != -1 && v != start) {
                path.add(v)
                v = parent[v]
            }
            path.add(start)
            path.reverse()
            return path.joinToString(" -> ") { nodes[it].toString() }
        }

        fun dfs(u: Int) {
            color[u] = 1
            graph[u].forEach { v ->
                if (color[v] == 0) {
                    parent[v] = u
                    dfs(v)
                } else if (color[v] == 1) {
                    // back-edge -> cycle
                    val cyclePath = buildCyclePath(v, u)
                    throw IllegalArgumentException("Dependency cycle detected: $cyclePath")
                }
            }
            color[u] = 2
        }
        for (i in nodes.indices) if (color[i] == 0) dfs(i)
    }

    private fun isReachable(from: ProjectPath, to: ProjectPath): Boolean {
        if (from == to) return true
        val visited = mutableSetOf<ProjectPath>()
        val stack = ArrayDeque<ProjectPath>()
        stack.add(from)
        while (stack.isNotEmpty()) {
            val u = stack.removeLast()
            if (!visited.add(u)) continue
            if (u == to) return true
            val deps = dependencyGraph[u].orEmpty()
            deps.forEach { stack.add(it) }
        }
        return false
    }

    private fun resolveAgainstCurrentComponent(buildName: String): ProjectPath {
        return if (ProjectPath.isFullPath(buildName)) {
            ProjectPath.parse(buildName)
        } else {
            currentComponent.append(buildName)
        }
    }
    // endregion

    // Test-only constructor
    internal constructor(
        allBuildConfigurations: List<String>,
        isSingleConfiguration: Boolean
    ) {
        this.allBuildConfigurations = allBuildConfigurations.map { ProjectPath.parse(it) }
        this.isSingleConfiguration = isSingleConfiguration
        this.pipelineSettingsByProject = emptyMap()
        this.dependencyGraph = this.allBuildConfigurations.associateWith { emptySet<ProjectPath>() }
    }
}