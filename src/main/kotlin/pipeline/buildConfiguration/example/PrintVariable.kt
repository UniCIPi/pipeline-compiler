package fanci.compiler.pipeline.buildConfiguration.example

import fanci.compiler.pipeline.buildConfiguration.AbstractBuildConfiguration
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.DisplayType
import fanci.compiler.pipeline.Parameter
import fanci.compiler.pipeline.SelectParameter
import fanci.compiler.pipeline.TextParameter
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.PrintVariable

class PrintVariable(
    buildConfigurationDefinition: BuildConfigurationDefinition,
    override val buildTypeDefinition: PrintVariable
) : AbstractBuildConfiguration(buildConfigurationDefinition) {
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
                echo "Configuration variable: %fanciGetParam(configurationVariable)%"
                echo "Prompt variable: %fanciGetParam(promptVariable)%"
                %fanciSetParam(changedVariable, "This variable has been changed in the build step")%
                echo "Not yet changed ChangedVariable: %fanciGetParam(changedVariable)%"
            """.trimIndent()
        ),
        BuildStep(
            name = "Print changed variable",
            script = """
                echo "Changed variable: %fanciGetParam(changedVariable)%"
            """.trimIndent()
        )
    )
}