package com.bringauto.pipelinecompiler.pipeline.buildConfiguration.example

import com.bringauto.pipelinecompiler.pipeline.buildConfiguration.AbstractBuildConfiguration
import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.DisplayType
import com.bringauto.pipelinecompiler.pipeline.Parameter
import com.bringauto.pipelinecompiler.pipeline.SelectParameter
import com.bringauto.pipelinecompiler.pipeline.TextParameter
import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.PrintVariable

class PrintVariable(
    buildStepConfiguration: BuildStepConfiguration,
    override val buildTypeDefinition: PrintVariable
) : AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "Print variable"
    override val parameters: List<Parameter> = listOf(
        TextParameter(
            name = "configurationVariable",
            displayType = DisplayType.NORMAL,
            value = buildTypeDefinition.configurationVariable,
            description = "Variable to print"
        ),
        SelectParameter(
            name = "promptVariable",
            displayType = DisplayType.PROMPT,
            value = buildTypeDefinition.promptVariable,
            description = "Variable to prompt for",
            label = "Prompt Variable",
            options = listOf("Option 1", "Option 2", "Option 3"),
            allowMultiple = false
        ),
        Parameter(
            name = "changedVariable",
            value = "This is a param variable, that will be changed",
        )
    )
    override val buildSteps: List<BuildStep> = listOf(
        BuildStep(
            name = "Print variable",
            script = """
                echo "Configuration variable: %pipelineGetParam(configurationVariable)%"
                echo "Prompt variable: %pipelineGetParam(promptVariable)%"
                %pipelineSetParam(changedVariable, "This variable has been changed in the build step")%
                echo "Not yet changed ChangedVariable: %pipelineGetParam(changedVariable)%"
            """.trimIndent()
        ),
        BuildStep(
            name = "Print changed variable",
            script = """
                echo "Changed variable: %pipelineGetParam(changedVariable)%"
            """.trimIndent()
        )
    )
}