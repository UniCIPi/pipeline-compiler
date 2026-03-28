package com.bringauto.pipelinecompiler.generator.platforms.teamcity

import com.bringauto.pipelinecompiler.common.ProjectPath
import com.bringauto.pipelinecompiler.common.ProjectPath.Companion.PROJECT_PATH_DELIMITER
import com.squareup.kotlinpoet.ClassName
import dev.turingcomplete.textcaseconverter.StandardTextCases
import dev.turingcomplete.textcaseconverter.StandardWordsSplitters
import dev.turingcomplete.textcaseconverter.toTextCase

internal object NamingUtils {
    /**
     * Normalizes the given name to PascalCase.
     * It splits the name by underscores ('_'), converts each part to PascalCase,
     * and then joins them back with underscores.
     *
     * Each part divided by underscore represents a level in the project hierarchy.
     *
     * @param name The name to normalize.
     * @return The normalized name in PascalCase.
     */
    internal fun normalizeName(name: String): String {
        return name
            .split(PROJECT_PATH_DELIMITER)
            .filter { it.isNotBlank() } // ignore the implicit root (leading "::")
            .joinToString("_") {
                it.toTextCase(
                    StandardTextCases.PASCAL_CASE,
                    originWordsSplitter = StandardWordsSplitters.STRICT_UPPER_CASE,
                    targetWordsDelimiter = " "
                )
                    .toTextCase(
                        StandardTextCases.PASCAL_CASE,
                        originWordsSplitter = StandardWordsSplitters.DASH,
                        targetWordsDelimiter = " "
                    )
                    .toTextCase(StandardTextCases.PASCAL_CASE)
            }
    }

    /**
     * Extracts the project name from the given name.
     * Removes the project hierarchy prefix (everything before the last underscore).
     */
    internal fun extractProjectName(projectFullPath: String): String {
        return ProjectPath.projectOfExcludeParents(projectFullPath)
    }

    internal fun createVcsRootClassName(projectName: String) = ClassName("${normalizeName(projectName)}.vcsRoots", createVcsRootName(projectName))
    internal fun createVcsRootName(projectName: String) = "${normalizeName(projectName)}_VCS"
}