package generator

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.configurationModel.SubmoduleStrategy
import fanci.compiler.configurationModel.buildTypeDefinitions.PrintVariable
import fanci.compiler.generator.DependencyManager
import fanci.compiler.pipeline.Dependency
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyManagerTest {
    /**
     * Dependency resolution tests
     */
    val buildConfigurationList = listOf("::Root::Project1::Build", "::Root::Build", "::Root::Project2::Build1", "::Root::Project2::Build2")
    var dependencyManager = DependencyManager(buildConfigurationList, false)
    @Test
    fun getProject_TestMulti() {
        val input = "::Root::Project::Build"
        val expectation = "::Root::Project"
        val result = dependencyManager.getProject(input)
        assertEquals(expectation, result)
    }
    @Test
    fun getProject_TestRoot() {
        val input = "::Root::Build"
        val expectation = "::Root"
        val result = dependencyManager.getProject(input)
        assertEquals(expectation, result)
    }

    @Test
    fun getBuildType_TestRoot() {
        val input = "::Root::Build"
        val expectation = "Build"
        val result = dependencyManager.getBuildType(input)
        assertEquals(expectation, result)
    }

    @Test
    fun findDependency_FullPathExists() {
        val result = dependencyManager.findDependency("::Root::Project1::Build")
        assertEquals("::Root::Project1::Build", result)
    }

    @Test
    fun findDependency_FullPathInvalidProject() {
        val ex = assertFailsWith<IllegalArgumentException> {
            dependencyManager.findDependency( "::Root::Invalid::Build")
        }
        assertTrue(ex.message!!.contains("Project"))
    }

    @Test
    fun findDependency_FullPathInvalidBuild() {
        val ex = assertFailsWith<IllegalArgumentException> {
            dependencyManager.findDependency("::Root::Project1::InvalidBuild")
        }
        assertTrue(ex.message!!.contains("Build configuration"))
    }
    
    @Test
    fun findDependency_RelativePathExists() {
        dependencyManager.setCurrentComponent("::Root::Project2")
        val result = dependencyManager.findDependency( "Build1")
        assertEquals("::Root::Project2::Build1", result)
    }

    @Test
    fun findDependency_RelativePathInvalidBuild() {
        dependencyManager.setCurrentComponent("::Root::Project2")
        val ex = assertFailsWith<IllegalArgumentException> {
            dependencyManager.findDependency("InvalidBuild")
        }

        assertTrue(ex.message!!.contains("Build configuration"))
    }

    /**
     * Cycle, isDepepndencyOfTests
     */
    /**
     * Helper to create a PipelineSettings with a single build configuration named "Print variable".
     * [componentName] must be a fully qualified project path (e.g. ::Root::A)
     * [deps] are dependency names as provided in configuration (may be relative or fully qualified).
     */
    private fun mkPipeline(componentName: String, deps: List<String>): PipelineSettings {
        val def = PrintVariable(configurationVariable = "x", promptVariable = "y")
        val depObjs = deps.map { name -> Dependency(name, Dependency.FailureAction.FAIL, artifacts = null) }
        val bsc = BuildConfigurationDefinition(
            buildTypeDefinition = def,
            dependencies = depObjs,
            triggers = emptyList<Trigger>(),
            freeDiskSpaceGb = 0,
            dockerConnections = emptyList(),
            buildNumberPattern = null
        )

        val input = InputConfiguration(
            variables = null,
            component = fanci.compiler.configurationModel.Component(
                name = componentName,
                repository = "https://example.com/repo.git",
                defaultBranch = "main",
                vcsAuthMethod = null,
                submoduleStrategy = SubmoduleStrategy.none,
                buildConfigurations = listOf(bsc)
            )
        )
        return PipelineSettings(input)
    }

    @Test
    fun isDependencyOf_transitiveAcrossComponents() {
        // Components: ::Root::A depends on ::Root::B, and ::Root::B depends on ::Root::C
        val psC = mkPipeline("::Root::C", deps = emptyList())
        val psB = mkPipeline("::Root::B", deps = listOf("::Root::C::Print variable"))
        val psA = mkPipeline("::Root::A", deps = listOf("::Root::B::Print variable"))

        val dm = DependencyManager(listOf(psA, psB, psC))

        // Use A as current component for resolving relative names in calls
        dm.setCurrentComponent("::Root::A")

        // C is a transitive dependency of A through B
        assertTrue(dm.isDependencyOf("::Root::C::Print variable", "::Root::A::Print variable"))
        // Relative arguments: same check
        assertTrue(dm.isDependencyOf("Print variable", "::Root::A::Print variable").not()) // self is not dependency
        assertTrue(dm.isDependencyOf("::Root::B::Print variable", "::Root::A::Print variable"))
        // Negative case: A is not a dependency of C
        assertFalse(dm.isDependencyOf("::Root::A::Print variable", "::Root::C::Print variable"))
    }

    @Test
    fun isDependencyOf_relativeResolutionAgainstCurrentComponent() {
        val psX = mkPipeline("::Root::X", deps = emptyList())
        val psY = mkPipeline("::Root::Y", deps = listOf("::Root::X::Print variable"))
        val dm = DependencyManager(listOf(psX, psY))

        dm.setCurrentComponent("::Root::X")
        // When current is ::Root::X, relative name "Print variable" resolves to ::Root::X::Print variable
        assertTrue(dm.isDependencyOf("Print variable", "::Root::Y::Print variable"))

        dm.setCurrentComponent("::Root::Y")
        // Now relative "Print variable" resolves to ::Root::Y::Print variable
        assertFalse(dm.isDependencyOf("Print variable", "::Root::Y::Print variable")) // self is never dependency
    }

    @Test
    fun constructor_detectsCycle() {
        // A -> B, B -> A
        val psA = mkPipeline("::Root::A", deps = listOf("::Root::B::Print variable"))
        val psB = mkPipeline("::Root::B", deps = listOf("::Root::A::Print variable"))

        val ex = assertFailsWith<IllegalArgumentException> {
            DependencyManager(listOf(psA, psB))
        }
        assertTrue(ex.message!!.contains("Dependency cycle detected"))
    }

    @Test
    fun constructor_rejectsSelfDependency() {
        val ps = mkPipeline("::Root::Self", deps = listOf("Print variable"))
        val ex = assertFailsWith<IllegalArgumentException> {
            DependencyManager(listOf(ps))
        }
        assertTrue(ex.message!!.contains("cannot depend on itself"))
    }

    @Test
    fun constructor_reportsUnknownDependency() {
        val ps = mkPipeline("::Root::A", deps = listOf("::Root::B::NonExisting"))
        val ex = assertFailsWith<IllegalArgumentException> {
            DependencyManager(listOf(ps))
        }
        // It should mention that the project doesn't exist or build configuration not found
        assertTrue(ex.message!!.contains("Project") || ex.message!!.contains("Build configuration"))
    }
}