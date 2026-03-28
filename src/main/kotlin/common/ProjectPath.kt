package com.bringauto.pipelinecompiler.common

import java.io.File
import java.nio.file.Path

/**
 * Immutable representation of a hierarchical project path delimited by "::".
 *
 * The leading "::" indicates the implicit root. That means:
 * - "::"                         -> root (no segments)
 * - "::Project"                  -> one segment under root
 * - "::Project::Component::Build"-> deeper hierarchy
 */
data class ProjectPath (
    val segments: List<String> // empty list represents the root "::"
) {
    override fun toString(): String =
        if (segments.isEmpty()) PROJECT_PATH_DELIMITER
        else segments.joinToString(PROJECT_PATH_DELIMITER, prefix = PROJECT_PATH_DELIMITER)


    /**
     * Returns a path without the last segment.
     * Root stays root.
     */
    fun withoutLast(): ProjectPath =
        when {
            segments.isEmpty() -> this
            segments.size == 1 -> ProjectPath(emptyList())
            else -> ProjectPath(segments.dropLast(1))
        }

    /**
     * Last segment name (e.g. "Build"). Throws if called on root path.
     */
    fun last(): String {
        require(segments.isNotEmpty()) { "Root path '::' has no last segment." }
        return segments.last()
    }

    fun append(segment: String): ProjectPath {
        require(segment.isNotBlank()) { "Segment must not be blank" }
        require(!segment.contains(PROJECT_PATH_DELIMITER)) { "Segment must not contain '$PROJECT_PATH_DELIMITER': '$segment'" }
        return ProjectPath(segments + segment)
    }

    fun startsWith(other: ProjectPath): Boolean =
        if (other.segments.isEmpty()) true // everything starts with root
        else segments.size >= other.segments.size && segments.subList(0, other.segments.size) == other.segments

    companion object {
        const val PROJECT_PATH_DELIMITER = "::"

        /**
         * Parse strings like "::", "::A", "::A::B::C".
         * Requires a leading "::". Accepts "::" for root.
         */
        fun parse(raw: String): ProjectPath {
            val v = raw.trim()
            require(v.startsWith(PROJECT_PATH_DELIMITER)) {
                "ProjectPath must start with '$PROJECT_PATH_DELIMITER' (was '$raw')"
            }
            // split keeps empty heads/tails; filter removes empties to build segments
            val parts = v.split(PROJECT_PATH_DELIMITER).filter { it.isNotBlank() }
            // Root "::" -> empty parts -> allowed
            // Validate mid segments are not empty (already enforced by filter)
            parts.forEach {
                require(!it.contains(PROJECT_PATH_DELIMITER)) { "Segment must not contain '$PROJECT_PATH_DELIMITER': '$it'" }
            }
            return ProjectPath(parts)
        }

        /**
         * Create from segments (no leading "::" needed). Empty means root.
         */
        fun of(vararg parts: String): ProjectPath {
            parts.forEach {
                require(it.isNotBlank()) { "Segment must not be blank" }
                require(!it.contains(PROJECT_PATH_DELIMITER)) { "Segment must not contain '$PROJECT_PATH_DELIMITER': '$it'" }
            }
            return ProjectPath(parts.toList())
        }

        /**
         * Build a ProjectPath representing only the parent hierarchy of a YAML file
         * relative to a root directory, e.g., root/A/B/my-config.yaml -> ::A::B
         * If there are no parent directories, returns root ("::").
         */
        fun fromDirectoryHierarchy(rootDir: File, yamlFile: File): ProjectPath {
            val rel: Path = rootDir.toPath().relativize(yamlFile.toPath())
            val parentSegments = rel.parent?.map { it.fileName.toString() } ?: emptyList()
            return ProjectPath(parentSegments)
        }

        /**
         * Return the project path part (everything except the last segment).
         * Root stays root.
         */
        fun projectOf(fullPath: ProjectPath): ProjectPath = fullPath.withoutLast()

        /**
         * Return the build type (last segment). Throws for root.
         */
        fun buildTypeOf(fullPath: ProjectPath): String = fullPath.last()

        /**
         * Return the project name (last segment). Throws for root.
         * same as buildTypeOf(), added for more clarity in usage.
         */
        fun projectOfExcludeParents(fullPath: ProjectPath): String = fullPath.last()

        /**
         * Return the project name (last segment). Overload for string
         */
        fun projectOfExcludeParents(fullPath: String): String = projectOfExcludeParents(parse(fullPath))


        /**
         * True if the dependency string already contains the delimiter (i.e., a hierarchical path).
         */
        fun isFullPath(name: String): Boolean = name.contains(PROJECT_PATH_DELIMITER)

        /**
         * Canonical compare between two hierarchical names. Keeps hierarchy "::" as-is,
         * strips non-alphanumerics from segments, compares case-insensitively.
         */
        fun equalsCanonical(a: String, b: String): Boolean = canonicalize(a) == canonicalize(b)

        fun canonicalize(s: String): String {
            if (s.isBlank()) return ""
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                if (ch.isLetterOrDigit()) {
                    out.append(ch.lowercaseChar())
                } else if (ch == ':' && i + 1 < s.length && s[i + 1] == ':') {
                    out.append(PROJECT_PATH_DELIMITER)
                    i++ // skip the second ':'
                }
                i++
            }
            return out.toString()
        }
    }
}
