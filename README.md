# Pipeline compiler

Generate a CI/CD pipeline for any platform using a simple YAML file.

Configure repository and build steps in a single YAML file. The generator will convert it into platform specific build configuration.

## How it works

![](doc/resources/ActivityDiagram.svg)


## Usage

### Build

To build the project, run the following command:

```bash

./gradlew build
```

### Run

```bash
./gradlew run --args="--config-dir=<path> --output-dir=<path> --target-platform=<platform> [--platform-env-file=<path>] [--generate-diff=<path>]"
```

### Arguments

- **-c | --config-dir** `<path>`: Path to a directory with one or more YAML configuration files. All configuration files in the directory will be processed.
- **-o | --output-dir** `<path>`: Path to the output directory where the generated configuration will be saved. The directories will be created.
- **-t | --target-platform** `<platform>`: The target platform. Supported platforms are:
    - `TeamCity`
    - `GitLab`
- **-e | --platform-env-file** `<path>`: Path to the platform environment file.
    - This file contains secrets and other platform specific settings.
    - If not provided, the generator will use values from the input configuration without checking if they are defined, or placeholders.
- **-d | --generate-diff** `<path>`: Generates a diff report between the current `--output-dir` and the newly generated output (built in a temporary directory) and saves it to the specified directory.
- **-b | --create-backup** `<bool>`: Creates a backup of the current `--output-dir` before replacing it with newly generated files. Default: `true`
- **-i | --ignore-invalid**: Continue generation even if some configurations are invalid. Invalid pipelines are skipped with a warning; otherwise the run stops on the first error.
- **--configuration** `<path>`: Path to the YAML configuration file. Mutually exclusive with `--config-dir`. For testing purposes.
- **--help**: Prints the help message.


## Configuration

The configuration file is a YAML file that defines the repository, build steps, and other settings. The structure of the configuration file is as follows:

```yaml
component:
  name: "Project name"
  repository: "repositoryUrl"
  ssh-key-name: "keyName" # Optional, if not set, the default key will be used
  submodule-strategy: recursive # recursive, shallow, none
  build:
    - type: "<BuildType>" # Type of the build step, see below for more information
      # <buildTypeDefinition-SpecificFields> # Specific fields for the build type
      dependencies: # Optional, dependencies for the build step
      free-space-gb: # Optional, required free space for the build step in GB
      docker-connection: # Optional, list of Docker connection IDs
```

> Note:
> all keys in yaml use snake-case, but in Kotlin code they are converted to camelCase.

### Build types

Implemented build types configurations are specified in `src/main/configurationModel/buildTypeDefinitions`

More documentation on build types is in [BuildTypes.md](doc/BuildTypes.md). This document describes the build types and their configuration, and usage of special build types.

### Component chaining

The generator supports chaining of components and their build configurations.

The chaining enables to create multi-component build chains, that are useful for testing and deployment of complex systems. 

To use build configurations from different components in a chain, in the dependencies section of the build configuration, 
use fully qualified names of the build configurations. 
```yaml
dependencies:
  - build-configuration-name: ::ParentProject::Component::Build
    failure-action: FAIL
    artifacts:  # Optional
      - source: "*" # Artifacts path of the previous step
        destination: "ComponentAArtifacts/" # Destination path in the current step, where the artifacts will be copied to
```

Documentation on chaining is in [Chaining.md](doc/BuildChaining.md).

It is also possible to use Parameters from different build configurations in the chain, by using their fully qualified names.

## Authentication

Setting up authentication is handled by the platform specific configuration and environment file.

Environment files are described in [ManageSecrets.md](doc/ManageSecrets.md).

If no environment file is provided, the generator will use values from configuration without checking their presence. 
If no configuration values are present, the generator will use placeholders.

## Implementation documentation

Implementation design and architecture is described in [HighLevelArchitectureOverview.md](doc/HighLevelArchitectureOverview.md).

The pipeline compiler generates pipeline settings based on a formal model defined in [FormalModel.md](doc/FormalModel.md).
This model is represented in `pipeline` package. It describes the meaning of distinct fields of the pipeline configuration.

The model is based on TeamCity and TeamCity implements the behaviour defined in the model. 
GitLab implementation differences and solutions are described in [GitLabImplementation.md](doc/GitLabImplementation.md).

## Platform compatibility

GitLab does not support build configurations with multiple docker images defined.