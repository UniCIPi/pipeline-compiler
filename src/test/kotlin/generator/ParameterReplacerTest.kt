package fanci.compiler.generator

import fanci.compiler.common.ProjectPath
import fanci.compiler.pipeline.Artifact
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.Dependency
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.Parameter
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.PullRequestConfiguration
import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.configurationModel.Component
import fanci.compiler.configurationModel.SubmoduleStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParameterReplacerTest {

    private class MockBuildConfiguration(
        override val name: String,
        override val buildSteps: List<BuildStep>,
        override val parameters: List<Parameter> = emptyList(),
        override val publishArtifacts: List<Artifact> = emptyList(),
        override val dependencies: List<Dependency> = emptyList(),
        override var dockerImage: String = ""
    ) : IBuildConfiguration {
        override val freeDiskSpaceGb = 0
    }

    @Test
    fun `test replaceParametersInPipeline resolves platform parameters in run-conditions`() {
        // Arrange
        val condition = BuildStep.RunCondition(
            parameterName = "FANCI_DEFAULT_BRANCH",
            operation = BuildStep.Operation.NotEqual,
            value = "%fanciGetParam(FANCI_BUILD_BRANCH)%"
        )
        
        val condition2 = BuildStep.RunCondition(
            parameterName = "MY_CUSTOM_PARAM",
            operation = BuildStep.Operation.Equal,
            value = "some-value"
        )
        
        val step = BuildStep(
            name = "test-step",
            script = "echo %fanciGetParam(FANCI_BUILD_NUMBER)%",
            runConditions = listOf(condition, condition2)
        )
        
        val buildConfig = MockBuildConfiguration(
            name = "test-build",
            buildSteps = listOf(step)
        )
        
        val inputConfig = InputConfiguration(
            variables = emptyMap(),
            component = Component(
                name = "test-component",
                repository = "http://git.example.com",
                submoduleStrategy = SubmoduleStrategy.none,
                buildConfigurations = emptyList()
            )
        )
        
        // We need a real PipelineSettings because it's not an interface
        val pipelineSettings = PipelineSettings(inputConfig)
        // Manually inject our mock build config
        val field = PipelineSettings::class.java.getDeclaredField("buildConfigurations")
        field.isAccessible = true
        field.set(pipelineSettings, listOf(buildConfig))

        val platformParameters = mapOf(
            "FANCI_DEFAULT_BRANCH" to "vcsroot.branch",
            "FANCI_BUILD_BRANCH" to "teamcity.build.branch",
            "FANCI_BUILD_NUMBER" to "build.number"
        )

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name -> "%$name%" },
            setParameterFormatFunc = { name, value -> "set $name=$value" },
            platformParameters = platformParameters
        )

        // Assert
        assertEquals("vcsroot.branch", condition.parameterName)
        assertEquals("%teamcity.build.branch%", condition.value)
        assertEquals("MY_CUSTOM_PARAM", condition2.parameterName)
        assertEquals("some-value", condition2.value)
        assertEquals("echo %build.number%", step.script)
    }

    @Test
    fun `test script parameter replacement and dependency resolution for TeamCity`() {
        // Arrange
        val step = BuildStep(
            name = "step1",
            script = "echo %fanciGetParam(PARAM1)% && %fanciSetParam(PARAM2, val2)%"
        )
        val buildConfig = MockBuildConfiguration(
            name = "build1",
            buildSteps = listOf(step)
        )
        val pipelineSettings = createPipelineSettings(listOf(buildConfig))

        val platformParameters = mapOf("PARAM1" to "tc.param1")

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name ->
                if (ProjectPath.isFullPath(name)) {
                    val path = ProjectPath.parse(name)
                    "%dep.Project_${path.segments[0]}.${path.last()}%"
                } else "%$name%"
            },
            setParameterFormatFunc = { name, value -> "echo \"##teamcity[setParameter name='$name' value='$value']\"" },
            platformParameters = platformParameters
        )

        // Assert
        assertEquals("echo %tc.param1% && echo \"##teamcity[setParameter name='PARAM2' value='val2']\"", step.script)
    }

    @Test
    fun `test single character parameter names are replaced`() {
        // Arrange
        val step = BuildStep(
            name = "step1",
            script = "echo %fanciGetParam(P)% && %fanciSetParam(Q, q1)%"
        )
        val buildConfig = MockBuildConfiguration(
            name = "build1",
            buildSteps = listOf(step)
        )
        val pipelineSettings = createPipelineSettings(listOf(buildConfig))

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name -> "%$name%" },
            setParameterFormatFunc = { name, value -> "echo \"##teamcity[setParameter name='$name' value='$value']\"" },
            platformParameters = emptyMap()
        )

        // Assert
        assertEquals("echo %P% && echo \"##teamcity[setParameter name='Q' value='q1']\"", step.script)
    }

    @Test
    fun `test dependency parameter replacement for GitLab`() {
        // Arrange
        val step = BuildStep(
            name = "step1",
            script = "echo %fanciGetParam(::OtherProj::OtherBuild::PARAM1)%"
        )
        val buildConfig = MockBuildConfiguration(
            name = "build1",
            buildSteps = listOf(step)
        )
        val pipelineSettings = createPipelineSettings(listOf(buildConfig))

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name ->
                val sanitized = name.replace(".", "_")
                if (ProjectPath.isFullPath(sanitized)) {
                    "\${${sanitized.replace("::", "_").trim('_')}}"
                } else "\${$sanitized}"
            },
            setParameterFormatFunc = { name, value -> "echo \"$name=$value\" >> build.env" },
            platformParameters = emptyMap()
        )

        // Assert
        assertEquals("echo \${OtherProj_OtherBuild_PARAM1}", step.script)
    }

    @Test
    fun `test parameter replacement in artifacts and docker image`() {
        // Arrange
        val artifact = Artifact(source = "build/%fanciGetParam(VERSION)%/*.zip", destination = "pub/%fanciGetParam(VERSION)%")
        val depArtifact = Artifact(source = "dep/%fanciGetParam(DEP_VER)%", destination = "local/%fanciGetParam(DEP_VER)%")
        val dep = Dependency(buildConfigurationName = "::Dep::Build", failureAction = Dependency.FailureAction.FAIL, artifacts = listOf(depArtifact))
        
        val step = BuildStep(
            name = "step1",
            script = "echo 1"
        )
        
        val buildConfig = MockBuildConfiguration(
            name = "build1",
            buildSteps = listOf(step),
            publishArtifacts = listOf(artifact),
            dependencies = listOf(dep),
            dockerImage = "my-registry:5000/image:%fanciGetParam(TAG)%"
        )
        val pipelineSettings = createPipelineSettings(listOf(buildConfig))

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name -> "%$name%" },
            setParameterFormatFunc = { name, value -> "set $name=$value" },
            platformParameters = emptyMap()
        )

        // Assert
        assertEquals("my-registry:5000/image:%TAG%", buildConfig.dockerImage)
        assertEquals("build/%VERSION%/*.zip", artifact.source)
        assertEquals("pub/%VERSION%", artifact.destination)
        assertEquals("dep/%DEP_VER%", depArtifact.source)
        assertEquals("local/%DEP_VER%", depArtifact.destination)
    }

    @Test
    fun `test parameter replacement in pull request token`() {
        // Arrange
        val inputConfig = InputConfiguration(
            variables = emptyMap(),
            component = Component(
                name = "test-component",
                repository = "https://gitlab.example.com/group/repository.git",
                submoduleStrategy = SubmoduleStrategy.none,
                buildConfigurations = emptyList(),
                pullRequest = PullRequestConfiguration(tokenId = "%fanciGetParam(PR_TOKEN)%")
            )
        )
        val pipelineSettings = PipelineSettings(inputConfig)

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name -> "%$name%" },
            setParameterFormatFunc = { name, value -> "set $name=$value" },
            platformParameters = emptyMap()
        )

        // Assert
        assertEquals("%PR_TOKEN%", pipelineSettings.pullRequestConfiguration.tokenId)
    }

    @Test
    fun `test pull request configuration without token is kept untouched`() {
        // Arrange
        val pipelineSettings = createPipelineSettings(emptyList())
        val original = pipelineSettings.pullRequestConfiguration

        // Act
        ParameterReplacer.replaceParametersInPipeline(
            pipelineSettings,
            getParameterFormatFunc = { name -> "%$name%" },
            setParameterFormatFunc = { name, value -> "set $name=$value" },
            platformParameters = emptyMap()
        )

        // Assert
        assertEquals(original, pipelineSettings.pullRequestConfiguration)
        assertNull(pipelineSettings.pullRequestConfiguration.tokenId)
    }

    private fun createPipelineSettings(buildConfigs: List<IBuildConfiguration>): PipelineSettings {
        val inputConfig = InputConfiguration(
            variables = emptyMap(),
            component = Component(
                name = "test-component",
                repository = "http://git.example.com",
                submoduleStrategy = SubmoduleStrategy.none,
                buildConfigurations = emptyList()
            )
        )
        val pipelineSettings = PipelineSettings(inputConfig)
        val field = PipelineSettings::class.java.getDeclaredField("buildConfigurations")
        field.isAccessible = true
        field.set(pipelineSettings, buildConfigs)
        return pipelineSettings
    }
}
