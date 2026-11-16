package fanci.compiler.common

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk

object DiffAndBackup {
    fun createBackup(outputDir: Path) {
        if (!outputDir.exists()) return

        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupDir = outputDir.absolute().parent.resolve("${outputDir.fileName}_backup_$timestamp")
        outputDir.toFile()
            .copyRecursively(backupDir.toFile(), overwrite = false)
        println("Created backup at: $backupDir")
    }


    fun generateDiffPatch(
        originalFile: File,
        revisedFile: File,
        outputFile: File,
        contextLines: Int = 3
    ) {
        require(originalFile.isFile) { "Original file not found: ${originalFile.path}" }
        require(revisedFile.isFile) { "Revised file not found: ${revisedFile.path}" }

        val originalLines = originalFile.readLines()
        val revisedLines = revisedFile.readLines()

        val patch = DiffUtils.diff(originalLines, revisedLines)
        if (patch.deltas.isEmpty()) {
            return
        }
        val unified = UnifiedDiffUtils.generateUnifiedDiff(
            originalFile.path,
            revisedFile.path,
            originalLines,
            patch,
            contextLines
        )
        outputFile.parentFile.mkdirs()
        outputFile.writeText(unified.joinToString(System.lineSeparator()))
    }

    fun generateDiff(oldDir: Path, newDir: Path, diffDir: Path) {
        // TODO should the directory be deleted first?? Can remove important data, but if is full, then can lead to confusion
        println("Generating diff in directory: $diffDir")
        diffDir.createDirectories()
        oldDir.walk()
            .forEach { oldFile ->
                val relativePath = oldDir.relativize(oldFile)
                val newFile = newDir.resolve(relativePath)
                if (oldFile.isRegularFile() && newFile.exists()) {
                    val diffPath = diffDir.resolve(relativePath.parent ?: Path(""))
                    val outputFile = diffPath.resolve("${relativePath.fileName}.patch")
                        .toFile()
                    generateDiffPatch(oldFile.toFile(), newFile.toFile(), outputFile)
                }
            }
    }

}