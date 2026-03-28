package com.bringauto.pipelinecompiler.pipeline

object Validator {
    /** Validations aligned with the formal model */
    fun validateBuildConfigurations(pipelineSettings: PipelineSettings) {
        // Unique build configuration names
        val names = pipelineSettings.buildConfigurations.map { it.name }
        val dup = names.groupBy { it }.filter { it.value.size > 1 }.keys
        require(dup.isEmpty()) { "Duplicate build configuration names found: ${dup.joinToString()}" }

        // Parameter name uniqueness within each BC
        pipelineSettings.buildConfigurations.forEach { bc ->
            val pnames = bc.parameters.map { it.name }
            val pdup = pnames.groupBy { it }.filter { it.value.size > 1 }.keys
            require(pdup.isEmpty()) { "Duplicate parameter names in '${bc.name}': ${pdup.joinToString()}" }

            // Matrix validity: non-empty lists
            bc.matrix.forEach { (k, v) ->
                require(v.isNotEmpty()) { "Matrix dimension '$k' in '${bc.name}' must have a non-empty value list" }
            }
        }
    }
}