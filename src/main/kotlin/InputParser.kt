package fanci.compiler

import fanci.compiler.common.ProjectPath
import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.buildConfiguration.ScriptRunner
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.nio.file.Path
import java.text.ParseException

private const val scriptsDir = "scripts"

/**
 * The `InputParser` class is responsible for parsing configuration files or directories and translating
 * them into `PipelineSettings` objects. It validates and processes the input files, ensuring their
 * integrity and compatibility with the required structure and data format.
 *
 * The class supports parsing single configuration files as well as directories containing multiple
 * configuration files. It also validates project paths and performs script file resolution
 * for specific build configurations.
 */
class InputParser {
    val settingsFileExtensions = listOf<String>("yaml", "yml")
    val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    val pipelineSettingsList = mutableListOf<PipelineSettings>()

    /**
     * Parses the provided configuration file and converts it into a PipelineSettings object.
     *
     * The method reads and validates the content of the given configuration file. Then, it processes
     * the data to produce a PipelineSettings instance, performing additional operations such as
     * resolving and verifying associated script files for build configurations.
     *
     * @param configFile the configuration file to be parsed. It must be a valid file with the correct format.
     * @return the parsed PipelineSettings object containing the pipeline configuration derived from the input file.
     * @throws ParseException if the configuration file cannot be parsed or contains invalid data.
     * @throws IllegalArgumentException if there is an error converting the parsed data to PipelineSettings or processing related step scripts.
     */
    fun parseInputConfigFile(configFile: File): PipelineSettings {
        val configData: InputConfiguration = try {
            mapper.readValue<InputConfiguration>(configFile)
        } catch (e: Exception) {
            throw ParseException("Error parsing configuration file '${configFile.name}': ${e.message}", 0)
        }

        try {
            val pipelineSettings = PipelineSettings(configData)
            inlineStepScriptsForComponent(configFile, pipelineSettings)
            return pipelineSettings
        } catch (e: Exception) {
            throw IllegalArgumentException("Error processing configuration file '${configFile.name}' into pipeline settings: ${e.message}", e.cause)
        }
    }

    /**
     * Walks through the input directory, processes each file with an expected extension, and generates pipeline settings.
     *
     * @param configDirectory the path to the configuration directory containing settings files to be parsed
     * @param ignoreInvalid a flag indicating whether to continue processing even if an error occurs while parsing a file.
     *                      If false, processing halts upon encountering an error.
     * @return true if all valid configuration files are parsed successfully, or if the `ignoreInvalid` flag is true and
     *         any invalid files are skipped; false otherwise
     */
    fun parseConfigDirectory(configDirectory: Path, ignoreInvalid: Boolean = false) : Boolean {
        val root = configDirectory.toFile()
        if (!root.exists()) {
            println("Error: Configuration directory does not exist.")
            return false
        }
        if (!root.isDirectory) {
            println("Error: Provided path is not a directory.")
            return false
        }
        var success = true
        root.walkTopDown()
            .onFail { f, e -> println("Warning: Failed to access '${f.path}': ${e.message}") }
            .filter { it.isFile }
            .filter { file ->
                val ext = file.extension.lowercase()
                settingsFileExtensions.contains(ext)
            }
            .forEach { settingsFile ->
                try {
                    val settings = parseInputConfigFile(settingsFile)

                    checkProjectPath(settings.componentName, root, settingsFile)
                    pipelineSettingsList.add(settings)
                } catch (e: Exception) {
                    success = false
                    println(e.message)
                    if (e.cause != null) {
                        println("Cause: ${e.cause}")
                    }
                    if (!ignoreInvalid) {
                        println("Aborting pipeline generation.")
                        return false
                    }
                }
            }
        return success
    }


    /**
     * Inlines step scripts for the specified component into the associated build configurations that
     * use script runners.
     *
     * This method processes the `buildConfigurations` from `PipelineSettings` to identify instances
     * of `ScriptRunner`. For each build step of a `ScriptRunner` configuration, the script is taken
     * either from the `script-content` defined directly in the step configuration, or, if the step
     * defines a `file`, from the corresponding script file in the `scripts` directory adjacent to the
     * provided config file. The resulting script is then added to the script runner's build steps.
     *
     * @param configFile the configuration file specifying the pipeline component.
     *                   It is used to resolve the `scripts` directory in the component's parent directory
     * @param pipelineSettings the pipeline settings containing build configurations for which step
     *                         scripts need to be resolved and inlined.
     */
    private fun inlineStepScriptsForComponent(configFile: File, pipelineSettings: PipelineSettings) {
        val scriptsDir = configFile.parentFile.resolve(scriptsDir)
        val componentName = pipelineSettings.componentName

        pipelineSettings.buildConfigurations
            .filterIsInstance<ScriptRunner>()
            .forEach { runner ->
                runner.stepSpecifications.forEach { spec ->
                    val script = if (spec.isScriptInFile) {
                        resolveScriptFile(scriptsDir, spec.file, componentName).readText()
                    } else {
                        spec.scriptContent
                    }
                    runner.addStep(spec, script)
                }
            }
    }

    /**
     * Resolves and validates the existence of a script file for a specific component.
     *
     * This method locates a script file within the given scripts directory, ensuring
     * that it exists and is a valid file. If the file is not found, an exception is thrown.
     *
     * @param scriptsDir the directory where script files are expected to be located
     * @param scriptFileName the name of the script file to resolve
     * @param componentName the name of the project for which the script file is resolved; used for error reporting
     * @return the resolved script file object if it exists and is valid
     * @throws IllegalArgumentException if the script file cannot be found or is not a valid file
     */
    private fun resolveScriptFile(scriptsDir: File, scriptFileName: String, componentName: String): File {
        // Validate file name - disallow absolute paths, traversal, separators, and unsafe characters.
        val name = scriptFileName.trim()

        require(name.isNotEmpty()) {
            "Invalid script file name for component '$componentName': name cannot be blank."
        }
        require(!File(name).isAbsolute) {
            "Invalid script file name for component '$componentName': absolute paths are not allowed: '$name'."
        }
        require(!name.contains("../")) {
            "Invalid script file name for component '$componentName': path traversal is not allowed: '$name'."
        }
        require(!name.contains('/') && !name.contains('\\')) {
            "Invalid script file name for component '$componentName': directory separators are not allowed: '$name'."
        }
        val allowed = Regex("^[A-Za-z0-9._-]+$")
        require(allowed.matches(name)) {
            "Invalid script file name for component '$componentName': contains unsupported characters: '$name'. " +
                    "Allowed characters are letters, digits, dot (.), underscore (_), and dash (-)."
        }

        // Resolve and ensure it stays within scriptsDir (defense-in-depth with canonical paths)
        val baseDir = scriptsDir.canonicalFile
        val scriptFile = File(baseDir, name).canonicalFile
        require(scriptFile.parentFile == baseDir) {
            "Invalid script file path for component '$componentName': must resolve within '${baseDir.path}', was '${scriptFile.path}'."
        }

        require(scriptFile.isFile && scriptFile.canRead()) {
            "Script file not found or not readable for component '$componentName': ${scriptFile.absolutePath}"
        }
        return scriptFile

    }

    /**
     * Validates the consistency between a given project name and the directory structure of the root folder
     * and settings file. Throws an exception if the project name does not match the expected directory hierarchy.
     *
     * @param projectName the name of the project to validate, represented as a string.
     * @param rootDirectory the root directory to be used as a base for resolving the project path.
     * @param settingsFile the settings file whose location is checked against the resolved project path.
     * @throws IllegalArgumentException if the project name does not conform to the directory structure or expected naming conventions.
     */
    private fun checkProjectPath(projectName: String, rootDirectory: File, settingsFile: File) {
        val fullPath = ProjectPath.parse(projectName)
        val expectedProject = ProjectPath.fromDirectoryHierarchy(rootDirectory, settingsFile)
        require(ProjectPath.equalsCanonical(ProjectPath.projectOf(fullPath).toString(), expectedProject.toString())) {
                "Input structure error: Project name '$fullPath' does not match file path '${settingsFile.path}'. " +
                "Expected project name '${expectedProject.append(fullPath.last())}'."
        }
    }

}