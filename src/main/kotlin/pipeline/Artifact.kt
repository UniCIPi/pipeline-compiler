package fanci.compiler.pipeline

/**
 * Represents an artifact which can be:
 *  - produced by a build configuration and published as build artifact.
 *  - optionally consumed by dependents.
 * It is the pair (src, dstPath) of the input and output artifacts of the formal model.
 *
 * Notes:
 * - [source] is a path or pattern in the checkout directory to collect from.
 * - [destination] is an optional target directory; if null, the files are copied to the root of the artifacts
 *   when publishing, or to the checkout directory when consuming.
 * - Wildcards are supported and some archive formats can be addressed using the `!` package path syntax.
 *
 * Proposed feature, not implemented: optional dependency artifacts.
 * A dependency artifact rule must match at least one file, otherwise the build fails before its steps start.
 * An `optional` flag would let the rule match nothing. TeamCity marks such a rule by the `?:` prefix instead of `+:`,
 * GitLab would skip the check of the rule.
 */
data class Artifact(
    /**
     * The path to the artifact in the build environment
     *
     * Can use wildcards to specify files, e.g., `build/package*.zip`
     *
     * In dependency artifact, supports using package paths: `package.zip!path/to/file-in-package.txt`
     * Package paths are defined by exclamation mark '!' character.
     * Supported packages are zip, jar, tar, and tar.gz
     */
    var source: String,
    /**
     * Destination directory of the files matched by [source], without wildcards.
     * If not specified, the root of the artifacts when publishing, or the checkout directory when consuming.
     *
     * Only a part of the source path is kept under the destination:
     * a file is copied by its name only, a directory by its content,
     * and with wildcards the path is kept from the first segment containing a wildcard.
     */
    var destination: String? = null
)
