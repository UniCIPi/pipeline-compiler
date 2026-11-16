package fanci.compiler

import fanci.compiler.common.DiffAndBackup
import fanci.compiler.generator.DependencyManager
import fanci.compiler.generator.IPlatform
import fanci.compiler.generator.ParameterReplacer
import fanci.compiler.pipeline.Validator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.*
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Defines the command-line interface for the pipeline generator.
 * Initializes the configuration data and platform based on the provided arguments.
 */
class ArgParser : CliktCommand(name = "fanci-compiler") {
    val configFile by option("--configuration", help = "The config file to load").file(
        mustExist = true, canBeDir = false
    )

    val configDirectory by option("-c", "--config-dir", help = "The directory containing the configuration files").path(
        mustExist = true,
        canBeFile = false,
    )

    val outputDir by option("-o", "--output-dir", help = "The output directory").path(
        mustExist = false,
        canBeFile = false,
    )
        .required()
    val targetPlatform by option(
        "-t",
        "--target-platform",
        help = "The target platform to build for"
    ).enum<IPlatform.PlatformType>(ignoreCase = true)
        .required()
    val platformEnvFile by option(
        "-e",
        "--platform-env-file",
        help = "The platform environment file, containing names of tokens, SSH keys, etc.\n" +
               "If not provided, the generator will use values from the input configuration without checking if they are defined, or placeholders."
    ).file(mustExist = true, canBeDir = false)

    // Ignore input errors?? Are all errors from input?
    val ignoreErrors by option(
        "-i",
        "--ignore-invalid",
        help = "If set, errors during pipeline generation will be ignored and the generator will generate pipelines for all projects that can be generated successfully."
    ).flag(default = false)

    val generateDiff by option(
        "-d",
        "--generate-diff",
        help = "If set, and *output-dir* already exists the generator will generate a diff files in the directory, containing changes from the original files to the generated files."
    ).path(mustExist = false, canBeFile = false)

    val createBackup by option(
        "-b",
        "--create-backup",
        help = "If set, and *output-dir* already exists the generator will create a backup of the *output-dir* in the same directory."
    ).boolean()
        .default(true)

    override fun run() {
        if (configFile == null && configDirectory == null) {
            println("Error: Either a configuration file or a configuration directory must be provided.")
            exitProcess(1)
        } else if (configFile != null && configDirectory != null) {
            println("Error: Only one of configuration file or configuration directory can be provided.")
            exitProcess(1)
        }

        if (platformEnvFile == null) {
            println("INFO: No platform environment file provided, using placeholder values")
        }

        outputDir.createDirectories()
    }

    override val printHelpOnEmptyArgs = true
}

fun main(args: Array<String>) {
    val argParser = ArgParser()
    argParser.main(args)

    var everythingSuccessful = true
    val inputParser = InputParser()
    if (argParser.configFile != null) {
        println("Running with config file: ${argParser.configFile}")
        inputParser.parseInputConfigFile(argParser.configFile!!)
    } else {
        println("Running with config directory: ${argParser.configDirectory}")
        everythingSuccessful = inputParser.parseConfigDirectory(argParser.configDirectory!!, argParser.ignoreErrors)
        if (!everythingSuccessful) {
            if (inputParser.pipelineSettingsList.isEmpty()) {
                println("Error: No configuration data is loaded. Please check the configuration file.")
                exitProcess(1)
            }
            if (argParser.ignoreErrors) {
                println("Warning: Some pipelines settings are invalid. Flag --ignore-invalid is set. Continuing without them...")
            } else {
                println("Error: A pipelines settings is invalid. Please check the configuration file.")
                exitProcess(1)
            }
        }
    }

    val dependencyManager = try {
        DependencyManager(inputParser.pipelineSettingsList)
    } catch (e: Exception) {
        println("Error in dependency settings")
        println(e.message)
        exitProcess(1)
    }
    val platform = IPlatform.createPlatform(argParser.targetPlatform, dependencyManager, argParser.platformEnvFile)

    for (pipelineSettings in inputParser.pipelineSettingsList) {
        Validator.validateBuildConfigurations(pipelineSettings)
    }

    println()

    val tmpDir = Files.createTempDirectory("fanci-compiler")

    for (pipelineSettings in inputParser.pipelineSettingsList) {
        try {
            dependencyManager.setCurrentComponent(pipelineSettings.componentName)
            // TODO Add find dependency parameters, save them, check if dependency is set, in GitLab save parameters
            println("Replacing parameters in pipeline settings for project: ${pipelineSettings.componentName}")
            ParameterReplacer.replaceParametersInPipeline(
                pipelineSettings,
                getParameterFormatFunc = { variableName -> platform.parameterGetFormat(variableName) },
                setParameterFormatFunc = { variableName, value -> platform.parameterSetFormat(variableName, value) },
                platform.platformParameters)
            println("Generating pipeline for project: ${pipelineSettings.componentName} on platform: ${argParser.targetPlatform}")
            platform.generatePipeline(pipelineSettings, tmpDir)
            // TODO maybe replace variables after generating files
            println("Pipeline generated successfully for project: ${pipelineSettings.componentName} in temporary directory\n")


        } catch (e: Exception) {
            everythingSuccessful = false
            println("Error during pipeline generation: ${e.message}")
            if (argParser.ignoreErrors) {
                continue
            } else {
                break
            }
        }
    }


    if (everythingSuccessful || argParser.ignoreErrors) {
        if (argParser.outputDir.exists()) {
            if (argParser.createBackup && argParser.outputDir.toFile().list()?.isNotEmpty() == true) {
                DiffAndBackup.createBackup(argParser.outputDir)
            }
            if (argParser.generateDiff != null) {
                DiffAndBackup.generateDiff(argParser.outputDir, tmpDir, argParser.generateDiff!!)
            }
            argParser.outputDir.toFile()
                .deleteRecursively() // TODO Do not remove everything, as there might be files not from generator - how to solve if i remove a build in generator
        }

        tmpDir.toFile()
            .copyRecursively(
                target = argParser.outputDir.toFile(),
                overwrite = true
            )
        println("Successfully moved generated files to output directory: ${argParser.outputDir}")
        if (!everythingSuccessful) {
            println("Warning: Some pipelines were not generated.")
        }
    } else {
        println("Error: Some pipelines had errors during generation. See logs for details.")
        exitProcess(1)
    }

    tmpDir.toFile()
        .deleteRecursively()
}

