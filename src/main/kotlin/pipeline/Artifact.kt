package com.bringauto.pipelinecompiler.pipeline

/**
 * Represents an artifact which can be:
 *  - produced by a build configuration and published as build artifact.
 *  - optionally consumed by dependents.
 * See doc/FormalModel.md (section “Artifact”).
 *
 * Notes:
 * - [source] is a path or pattern in the checkout directory to collect from.
 * - [destination] is an optional target directory for publishing/export; if null, [source] is used as‑is.
 * - Wildcards are supported and some archive formats can be addressed using the `!` package path syntax.
 */
data class Artifact(
    /**
     * The path to the artifact in the build environment
     *
     * Can use wildcards to specify files, e.g., `build/package*.zip`
     *
     * In dependency artifact, supports using package paths: `package.zip!path/to/file-in-package.txt`
     * Package paths are defined by exclamation mark '!' character.
     * Supported packages are zip, 7zip, jar, tar, and tar.gz
     */
    var source: String,
    /**
     * Destination directory or path mapping for the artifact when publishing/consuming.
     * If not specified, the [source] path is used.
     */
    var destination: String? = null
)
