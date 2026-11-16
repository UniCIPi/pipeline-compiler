package fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins

class ReplacePlugin(
    private val replacements: List<Map<String, Any>>
) : IPlugin {
    override val name: String = "semantic-release-replace-plugin"

    override fun serializeConfiguration(): Map<String, Any> = mapOf(
        "replacements" to replacements
    )

    companion object {
        fun versionTxt() = ReplacePlugin(
            listOf(
                mapOf(
                    "files" to listOf("version.txt"),
                    "from" to "^version=.*",
                    "to" to "version=\${nextRelease.version}",
                    "results" to listOf(
                        mapOf(
                            "file" to "version.txt",
                            "hasChanged" to true,
                            "numMatches" to 1,
                            "numReplacements" to 1
                        )
                    ),
                    "countMatches" to true
                )
            )
        )

        fun toml(tomlFilePath: String) = ReplacePlugin(
            listOf(
                mapOf(
                    "files" to listOf(tomlFilePath),
                    "from" to "^version\\s?=\\s?\".*\"",
                    "to" to "version = \"\${nextRelease.version}\"",
                    "results" to listOf(
                        mapOf(
                            "file" to tomlFilePath,
                            "hasChanged" to true,
                            "numMatches" to 1,
                            "numReplacements" to 1
                        )
                    ),
                    "countMatches" to true
                )
            )
        )

        fun osRelease(osReleaseFilePath: String) = ReplacePlugin(
            listOf(
                mapOf(
                    "files" to listOf(osReleaseFilePath),
                    "from" to "VERSION_ID=.*",
                    "to" to "VERSION_ID=\"\${nextRelease.version}\"",
                    "results" to listOf(
                        mapOf(
                            "file" to osReleaseFilePath,
                            "hasChanged" to true,
                        )
                    )
                ),
                mapOf(
                    "files" to listOf(osReleaseFilePath),
                    "from" to "(PRETTY_NAME=\"[^\"]*?)\\d[\\d.]*\"",
                    "to" to "$1\${nextRelease.version}\"",
                    "results" to listOf(
                        mapOf(
                            "file" to osReleaseFilePath,
                            "hasChanged" to true,
                        )
                    )
                )
            )
        )
    }
}
