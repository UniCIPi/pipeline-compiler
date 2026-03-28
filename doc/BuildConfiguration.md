# Build Configuration

This document explains the functionality and usage of individual fields in [IBuildConfiguration.kt](../src/main/kotlin/pipeline/IBuildConfiguration.kt)

## Matrix

Matrix is a special type of parameter that allows the user to define a set of parameters.
When a matrix is defined, the pipeline will be executed for each combination of parameters.

The value of the matrix parameter can be used in build using get parameter function - `%pipelineGetParam(matrixKey)%`
This will get the value of matrix parameter for the current build.