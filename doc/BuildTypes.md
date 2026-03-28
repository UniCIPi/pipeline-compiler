# Build Types

This document describes how the build types are defined and used in the pipeline.

## Overview

- Defined in input YAML under `component.build[*].type`.
- Each type can have the following inputs:
  - [Common fields](#common-fields)
  - Type-specific fields defined in [configuration model -> build type definitions](../src/main/kotlin/configurationModel/buildTypeDefinitions).
- The inputs are used to create a build configuration, that can be transfered to the target platform's build configuration model.
- Build configurations are defined in [pipeline -> buildConfigurations](../src/main/kotlin/pipeline/buildConfiguration)

## Common fields

- dependencies: declare chaining and artifacts flow. See [BuildChaining.md](BuildChaining.md) for details.
```yaml
dependencies:
  - build-configuration-name: step1
    failure-action: FAIL
    artifacts:  # Optional
      - source: "*" # Artifacts path of the previous step
        destination: "step1Artifacts/" # Destination path in the current step, where the artifacts will be copied to
```
- free-space-gb: required free disk space on runner.
- docker-connection: list of docker connections
  - Necessary for pushing and pulling from private Docker registries.
  - Might be different for each platform
  - see [Authentication](../README.md#authentication) for more details
- build-number-pattern: pattern for build number generation, serves for identifying the build in the UI.
- triggers: triggers for the build configuration.
  - See [Triggers.md](Triggers.md) for details.

---

## Special build types

### Composite

Serves as input and output for the component build chain.

Used as a final step in a build chain, which serves as a single entry point for triggering a multistep process and collecting outputs:
1. Collect artifacts from all steps.
    * All collected artifacts are published as artifacts of the composite step.
2. Define triggers:
    * Each trigger triggers all dependencies in the build chain
3. Define parameters:
    * This step can be used to override parameters of dependencies.

Composite should be used both at:
- component level - aggregating outputs of component build configurations, used as dependency for other components.
- system level - aggregating outputs of all system's components.

Example:
```yaml
build:
  type: Composite
  configuration-name: "Compose Release"
  parameters:
    - name: release.version
      value: "1.2.3"
  dependencies:
    - build-configuration-name: ::Project::Lib::Build 
      failure-action: FAIL 
      artifacts:
        - source: "dist/**"
          destination: "lib/"
    - build-configuration-name: ::Project::App::Build
      failure-action: FAIL
      artifacts:
        - source: "package/*.tar.gz" 
          destination: "app/" 
```
With artifacts of dependencies:
```
::Project::Lib::Build
dist/
├── windows/
│   └── lib.dll
└── debian/
    └── lib.so

::Project::App::Build
package/
├── app-windows.tar.gz
└── app-debian.tar.gz
```
Compose will publish artifacts:
```
lib/
├── windows/
│   └── lib.dll
└── debian/
    └── lib.so
app/
├── app-windows.tar.gz
└── app-debian.tar.gz
```
### Script Runner

Purpose:
- Inline scripts as build steps into a build configuration.
- Single purpose tasks without creating a dedicated type.

Top-level fields:
- configuration-name: string — name of the build configuration.
- scripts: list — ordered list of script steps to execute.
- ssh-agent-key-name: string — if non-empty, enables ssh-agent and adds the specified key.
    - The key (with the same name) must be defined in the target platform.
- publish-artifacts: list — artifacts published by this build configuration (`{ source, destination? }`).
- parameters: list — parameters available to the build configuration (any type implementing `Parameter`).

Fields (per script step):
- step-name: string — display name of the step.
- file: string — script file name (searched in the sibling `scripts` directory).
- docker-image: string — optional container image to run the step in.
- docker-arguments: string — extra arguments passed to `docker run`.
- allow-docker-in-docker: boolean — allows Docker-in-Docker for this step (may be a security risk; use with caution).
- run-conditions: list — optional conditions that must be satisfied for the step to run.
    - Condition shape and supported operations are defined by `BuildStep.RunCondition`.

Example:
```yaml
build:
  - type: ScriptRunner
    configuration-name: "Script build configuration"
    scripts:
      - step-name: step 1
        file: script1.sh
      - step-name: step 2
        file: script2.sh
        docker-image: debian:latest
        docker-arguments: "-v %pipelineGetParam(VOLUME)%:%pipelineGetParam(VOLUME)%"
        allow-docker-in-docker: true
        run-conditions:
          - parameter-name: CI_COMMIT_BRANCH
            operation: Equal
            value: main
    docker-connections:
      - HubConnection
    parameters:
      - name: VOLUME
        value: "/srv/volume/"
    dependencies:
      - build-configuration-name: "Other build"
        failure-action: FAIL
        artifacts:
          - source: "*"
    free-space-gb: 20
    ssh-agent-key-name: "git-key"
    publish-artifacts:
      - source: "generated-package.tar"
```

---

## Other build types (examples)

Below are common categories you might model as build types. Include only what you use, and keep each type focused.

- Build/Package: compile, assemble, bundle, containerize.
- Test: unit, integration, hardware-in-the-loop.
- Lint/SAST: static analysis and security scans.
- Infra: environment setup, provisioning, image generation.

Each should:
- Declare clear inputs/outputs (parameters, artifacts).
- Avoid duplicating logic—prefer chaining and reuse.
- Keep runtime/environment config explicit (docker image, work-dir, free-space-gb).

---


## Creating new build types

1) Define input model
   - in `configurationModel/buildTypeDefinitions` create a new data class. Define yaml configuration inputs—the values that need to be configurable per project.
   - Keep the surface minimal and strongly typed.
2) Register the type to deserializer
   - Add the Build type to the `JsonSubTypes` in [IBuildTypeDefinition](../src/main/kotlin/configurationModel/buildTypeDefinitions/IBuildTypeDefinition.kt) interface file
3) Define Build configuration
   - Create a new class in [buildConfiguration](../src/main/kotlin/pipeline/buildConfiguration) that implements the `AbstractBuildConfiguration` class.
   - This class defines what the inputs are used for and defines the behaviour of the build type.
4) Register the build configuration
   - Add the build configuration to the `buildConfiguration` map in [BuildConfigurationFactory](../src/main/kotlin/pipeline/IBuildConfiguration.kt)

---

## Best practices

- Use Composite as a top-level orchestrator.
- Keep type-specific inputs minimal; favor convention over configuration.
- Use Standardized artifact locations and naming.
  - BringAuto standards are defined on [bringauto outline](https://outline.bringauto.com/doc/dev-standards-Qdxv3m5SwK)
- Enforce acyclic dependency graphs.
