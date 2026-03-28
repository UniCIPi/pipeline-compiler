package com.bringauto.pipelinecompiler.generator

import com.bringauto.pipelinecompiler.common.ProjectPath
import com.bringauto.pipelinecompiler.pipeline.Artifact
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.pipeline.PipelineSettings

object ParameterReplacer {

    /**
     * Replace parameter placeholders in the Pipeline settings with the actual parameter format of the target platform.
     *
     * Each platform implements parameterGetFormat and parameterSetFormat methods, which shall be passed as parameters.
     * @see IPlatform
     *
     * Parameter names must follow the regex pattern: [a-zA-Z_][a-zA-Z0-9._]*
     *
     * @param pipelineSettings The pipeline settings containing build configurations and other properties.
     * @param getParameterFormatFunc A function that takes a parameter name and returns it in the format required by the target platform.
     * @param setParameterFormatFunc A function that takes a parameter name and value and returns setter in the target platform format.
     * @param platformParameters A dictionary of parameter name mappings (Generic Name -> Platform Specific Name).
     */
    fun replaceParametersInPipeline(
        pipelineSettings: PipelineSettings,
        getParameterFormatFunc: (String) -> String,
        setParameterFormatFunc: (String, String) -> String,
        platformParameters: Map<String, String>
    ) {
        // Wrap the format functions to apply the platform parameter mapping first
        val mappedGetFunc: (String) -> String = { name ->
            getParameterFormatFunc(resolveEffectiveName(name, platformParameters))
        }

        val mappedSetFunc: (String, String) -> String = { name, value ->
            setParameterFormatFunc(resolveEffectiveName(name, platformParameters), value)
        }

        replaceParametersInBuildConfigurations(
            pipelineSettings.buildConfigurations,
            mappedGetFunc,
            mappedSetFunc
        )
    }

    private fun resolveEffectiveName(name: String, platformParameters: Map<String, String>): String {
        return if (ProjectPath.isFullPath(name)) {
           val path = ProjectPath.parse(name)

            val simpleName = path.last()
            val mappedSimpleName = platformParameters[simpleName] ?: simpleName

            // Reconstruct path: parent + mapped simple name
            val newPath = path.withoutLast().append(mappedSimpleName)
            
            newPath.toString()
        } else {
            platformParameters[name] ?: name
        }
    }

    /**
     * Replaces parameters in all build configurations.
     * This function iterates through each build configuration and replaces parameters in:
     *   - build steps,
     *   - triggers,
     *   - publish artifacts,
     *   - parameters,
     *   - dependencies artifacts.
     */
    private fun replaceParametersInBuildConfigurations(
        buildConfigurations: List<IBuildConfiguration>,
        getParameterFormatFunc: (String) -> String,
        setParameterFormatFunc: (String, String) -> String
    ) {
        buildConfigurations.forEach { buildConfig ->
            buildConfig.buildSteps.forEach { step ->
                step.dockerImage = replaceGetParameter(step.dockerImage, getParameterFormatFunc)
                step.dockerRunParameters = replaceGetParameter(step.dockerRunParameters, getParameterFormatFunc)
                step.workDir = replaceGetParameter(step.workDir, getParameterFormatFunc)
                step.script = replaceGetParameter(step.script, getParameterFormatFunc)
                step.script = replaceSetParameter(step.script, setParameterFormatFunc)
//                step.conditions.forEach { condition -> replaceGetParameter(condition.parameterName, getParameterFormatFunc) }
            }

            // Triggers are immutable data objects; parameter replacement is applied
            // later by specific platform generators when serializing values.
            // If needed, migrate to rebuilding the trigger list with replaced fields.

            buildConfig.publishArtifacts.replaceArtifactParameters(getParameterFormatFunc)

            buildConfig.parameters.forEach { parameter ->
                parameter.value = replaceGetParameter(parameter.value, getParameterFormatFunc)
            }

            buildConfig.dependencies.forEach { dependency ->
                dependency.artifacts?.replaceArtifactParameters(getParameterFormatFunc)
            }

            buildConfig.buildNumberPattern =
                buildConfig.buildNumberPattern?.let { pattern ->
                    replaceGetParameter(pattern, getParameterFormatFunc)
                }

            buildConfig.triggers.forEach { trigger ->
                trigger.parameters.forEach { param ->
                    param.value = replaceGetParameter(param.value, getParameterFormatFunc)
                }
            }
        }
    }

    /**
     * Replaces all occurrences of %pipelineGetParam(parameterName)% with the value returned by getParameterFunc.
     *
     * The parameter name must start with a letter and can contain letters, digits, underscores, and dots.
     * Dots are used by TeamCity and might not be allowed in other platforms.
     */
    private fun replaceGetParameter(input: String, getParameterFormatFunc: (String) -> String): String {
        return input.replace(Regex("%pipelineGetParam\\(([a-zA-Z_:][a-zA-Z0-9._: ]+)\\)%")) { matchResult ->
            getParameterFormatFunc(matchResult.groupValues[1])
        }

    }

    /**
     * Replaces all occurrences of %pipelineSetParam(parameter, value)% with the value returned by setParameterFunc.
     */
    private fun replaceSetParameter(input: String, setParameterFormatFunc: (String, String) -> String): String {
        return input.replace(Regex("%pipelineSetParam\\(([a-zA-Z_][a-zA-Z0-9._]+), (.*)\\)%")) { matchResult ->
            setParameterFormatFunc(
                matchResult.groupValues[1],
                matchResult.groupValues[2]
            )
        }
    }

    private fun List<Artifact>.replaceArtifactParameters(getParameterFormatFunc: (String) -> String) {
        this.forEach { artifact ->
            artifact.source = replaceGetParameter(artifact.source, getParameterFormatFunc)
            artifact.destination = artifact.destination?.let { replaceGetParameter(it, getParameterFormatFunc) }
        }
    }
}