package fanci.compiler.configurationModel.buildTypeDefinitions

data class PythonPackage(
    val imageMatrix: List<String>,
    val enableMypy: Boolean = true,
    val enableShivPackage: Boolean = true,
    val enableTests: Boolean = true,
    val packageModule: String?,
    val packageName: String?
) : IBuildTypeDefinition {
    init {
        if (enableShivPackage) {
            require(packageModule != null && packageName != null) {
                "When enabling shiv package, package-module and package-name must be specified"
            }
        }
    }
}
