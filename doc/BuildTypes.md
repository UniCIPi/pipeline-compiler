# Build Types

This document describes how the build types are defined and used in the pipeline.

## Overview

- Defined in input YAML under `component.build-configurations[*].type`.
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
- free-disk-space-gb: required free disk space on runner.
- docker-image: docker image defining the execution environment of the whole build configuration.
  - Realises the *container* of the formal model; Docker is the containerisation used by the supported platforms.
  - Every build step of the configuration is executed in it; a build step has no image of its own.
  - TeamCity has no configuration-wide image, so it is emitted on each generated step.
  - GitLab uses it as the job-level `image:`; a build configuration may not mix it with a different per-step image.
- docker-connections: list of docker connections
  - Necessary for pushing and pulling from private Docker registries.
  - Might be different for each platform
  - see [ManageSecrets.md](ManageSecrets.md) for more details
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
build-configurations:
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
- steps: list — ordered list of build steps to execute.
- ssh-agent-key-name: string — if non-empty, enables ssh-agent and adds the specified key.
    - The key (with the same name) must be defined in the target platform.
- publish-artifacts: list — artifacts published by this build configuration (`{ source, destination? }`).
- parameters: list — parameters available to the build configuration (any type implementing `Parameter`).

Fields (per build step):
- step-name: string — display name of the step.
- file: string — script file name (searched in the sibling `scripts` directory).
- script-content: string — script of the step written directly in the step configuration.
    - `file` and `script-content` are disjunctive — exactly one of them must be defined for each step.
- docker-arguments: string — extra arguments passed to `docker run`.
- allow-docker-in-docker: boolean — allows Docker-in-Docker for this step (may be a security risk; use with caution).
- run-conditions: list — optional conditions that must be satisfied for the step to run.
    - Condition shape and supported operations are defined by `BuildStep.RunCondition`.

Example:
```yaml
build-configurations:
  - type: ScriptRunner
    configuration-name: "Script build configuration"
    docker-image: debian:latest
    steps:
      - step-name: step 1
        file: script1.sh
      - step-name: step 2
        file: script2.sh
        docker-arguments: "-v %fanciGetParam(VOLUME)%:%fanciGetParam(VOLUME)%"
        allow-docker-in-docker: true
        run-conditions:
          - parameter-name: CI_COMMIT_BRANCH
            operation: Equal
            value: main
      - step-name: step 3
        script-content: |
          echo "The script is defined directly in the step configuration"
          echo "Build directory: %fanciGetParam(VOLUME)%"
    docker-connections:
      - HubConnection
    parameters:
      - name: VOLUME
        value: "/srv/volume/"
        type: text
    matrix:
      name:
        - "matrix-1"
        - "matrix-2"
    dependencies:
      - build-configuration-name: "Other build"
        failure-action: FAIL
        artifacts:
          - source: "*"
    free-disk-space-gb: 20
    ssh-agent-key-name: "git-key"
    publish-artifacts:
      - source: "generated-package.tar"
```

---

## Predefined build types

The predefined build types are templates of build configurations covering parts of the delivery lifecycle.
Each of them defines the build steps, parameters and output artifacts of the build configuration from a few
input fields. The [common fields](#common-fields) keep their meaning in all of them.

| `type` | Purpose | Input fields |
|---|---|---|
| `CMake` | Build, test (ctest) and package (cpack) a CMake project in every image of a matrix. | `name`, `image-matrix`, `build-type`, `enable-tests`, `create-package`, `cmake-parameters`, `cmake-lists-dir` |
| `GoTest` | Run the tests of a Go module. | `go-version` |
| `GoPackage` | Build and package a Go module for a matrix of systems and architectures. | `go-version`, `system-list`, `arch-list` |
| `PythonPackage` | Lint (ruff), type check (mypy) and test (pytest) a Python package, optionally package it by shiv. | `image-matrix`, `enable-mypy`, `enable-tests`, `enable-shiv-package`, `package-module`, `package-name` |
| `PyPiPublish` | Build the distributions of a Python package with Poetry and upload them to a package index. | `configuration-name`, `token-id`, `repository-url` |
| `DockerBuildAndPush` | Build a Docker image and push it to a registry. | `image-name`, `image-tag`, `docker-file-path`, `docker-context-dir`, `docker-build-script`, `docker-version` |
| `KubectlApply` | Apply manifests to a Kubernetes cluster. | `files`, `kubeconfig` |
| `SemanticRelease` | Create a release of the component by semantic-release. | `configuration-name`, `preset`, `version-file-path`, `repo-access-token-id` |

The input fields are documented in the classes of
[configuration model -> build type definitions](../src/main/kotlin/configurationModel/buildTypeDefinitions).
Examples are in [example-configurations/configurations/Templates](../example-configurations/configurations/Templates).

### Semantic Release

Creates a release of the component by [semantic-release](https://semantic-release.gitbook.io/semantic-release):
the next version is derived from the commit messages, the release notes are written to `CHANGELOG.md`,
the version file is updated, and the release is published in the VCS host system of the repository.

It is a build configuration like any other. It is listed in `build-configurations`, other build configurations
can depend on it, and it uses the common fields:
- `docker-image` is required. The image must contain semantic-release and the plugins of the preset.
- `dependencies` are the build configurations that must pass before the release is created.
- `triggers` replace the default trigger of the template, see below.

Type-specific fields:
- configuration-name: string, name of the build configuration. Default `Component release`.
- preset: string, required. Selects the set of semantic-release plugins and release branches.
  `BringAuto` is the only preset now: releases from the default branch of the component and from the maintenance branches `N.x` and `N.N.x`.
- version-file-path: string, file holding the version, e.g. `version.txt`, `pyproject.toml`, `Cargo.toml`.
  Optional, no file is updated when not set.
- repo-access-token-id: string, ID of the token with write access to the repository.

The template defines three parameters:
- `TOKEN`: secret referencing `repo-access-token-id`.
- `DRY_RUN`: `true` by default, so a build job started by hand or as a dependency only reports the release
  it would create.
- `RELEASE_RC_CONTENT`: the semantic-release configuration generated from the preset.

When the build configuration defines no `triggers`, the template adds a VCS trigger on the release branches
of the preset, which sets `DRY_RUN` to `false` and ignores changes of the version file and of `CHANGELOG.md`.
The trigger argument overrides the default value of the parameter, as defined by the formal model.

Example:
```yaml
build-configurations:
  - type: SemanticRelease
    configuration-name: "Release"
    preset: "BringAuto"
    docker-image: "registry.example.com/org/semantic-release:25"
    repo-access-token-id: "release-token"
    version-file-path: "version.txt"
    dependencies:
      - build-configuration-name: "Build"
        failure-action: FAIL
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
- Keep runtime/environment config explicit (docker-image, work-dir, free-disk-space-gb).

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
   - Add the build configuration to the factory `createBuildConfiguration` in [IBuildConfiguration](../src/main/kotlin/pipeline/IBuildConfiguration.kt)

---

## Best practices

- Use Composite as a top-level orchestrator.
- Keep type-specific inputs minimal; favor convention over configuration.
- Use Standardized artifact locations and naming.
- Enforce acyclic dependency graphs.
