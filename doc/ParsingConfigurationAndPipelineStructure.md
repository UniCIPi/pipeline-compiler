# Parsing configuration and pipeline structure 

This chapter describes how a pipeline is configured, parsed, and
transformed from input YAML configuration into the intermediate
representation (IR) represented by the `pipeline` package and defined by
the formal model in
Chapter [\[ch:formalModel\]](#ch:formalModel){reference-type="ref"
reference="ch:formalModel"}. It explains the configuration model, the
construction of build configurations, and the main data structures of
the parser and IR of the `PipelineCompiler`.

In this thesis, the term *pipeline IR* refers to the data structures of
the `pipeline` package. The root IR object for a single component is
`PipelineSettings`.

## Project hierarchy and identification

The input of the `PipelineCompiler` is a directory with the
structure and semantics defined in this section.

Each component is nested in a project. A project is a logical grouping
that can contain components and other nested projects. Dividing
components into projects enables the unique identification of components
by their full path. It also enhances security by enabling a CI/CD
platform administrator to assign fine-grained permissions to users only
for the projects they need to access.

Each component is defined by a single YAML configuration file. Projects
are defined implicitly by the directory structure and must be reflected
in the component name in the input configuration, by using a fully
qualified name, described below.

### Fully qualified name

Each component is named by a fully qualified name (FQN), consisting of
the full project path and the component name, separated by the delimiter
`::`. Build configurations and parameters extend this scheme. For
example,

-   build configuration FQN:
    $$\texttt{::Project::Component::BuildConfiguration}$$

-   parameter FQN:
    $$\texttt{::Project::Component::BuildConfiguration::Parameter}$$

FQNs are used to reference one component's entities from another
component when defining dependencies or accessing parameters of another
build configuration.

##### Root project

Each FQN starts with the root project. The root is the top-level project
in the hierarchy. All projects and components are nested under the root.
It is a nameless project, represented by a leading project delimiter
`::`.

## Configuration model

The `configurationModel` package defines the schema of the input
configuration. It contains data classes that represent the user's intent
before it is compiled into a concrete execution plan. This package
allows the input configuration to be strongly typed and validated during
parsing.

### Component pipeline configuration

The YAML configuration follows a *kebab-case* naming convention for all
keys. During parsing, these keys are mapped to the corresponding fields
of the configuration model data classes defined in the package
`configurationModel`, which are defined with *camelCase* names.

This mapping is handled automatically by the YAML deserialisation layer,
allowing the internal representation to follow idiomatic Kotlin naming
conventions while keeping the configuration readable and consistent with
common YAML practices.

The goal of the input YAML configuration is to express the logical
structure and relationships between distinct CI/CD processes in a
concise and declarative form. It describes *what* should be built, how
individual build types are parameterised, and how they depend on each
other.

The input configuration is unified for all supported CI/CD platforms and
is designed to generate consistent pipeline settings on each platform.

#### Component configuration schema

Each YAML file defines the CI/CD pipeline of a single component and
corresponds to an instance of the `InputConfiguration` class in the
configuration model.

At the top level, the component configuration specifies:

-   fully qualified name of the component,

-   VCS repository URL,

-   authentication details for the VCS repository,

-   default VCS branch,

-   submodule strategy,

-   list of build type definitions of the component.

An example of a component configuration is shown in
Listing [\[code:yamlGeneralStructure\]](#code:yamlGeneralStructure){reference-type="ref"
reference="code:yamlGeneralStructure"}.

```yaml 
component: 
  name: "::project::component"
  repository: "https://repository.url/path" 
  default-branch: main 
  vcs-auth-method:
    type: method-type 
    specific-data: method-specific-data
  submodule-strategy: none
  build: []
```

#### VCS authentication

The component configuration specifies authentication details required to
access the VCS repository. Authentication references secrets defined in
a target platform.

The following authentication methods are supported. The method type is selected by a `type` keyword:
-   `uploaded-key` - references a named SSH key set up in the platform and can set up a username,
-   `token` - references an authentication token defined in the target platform and can set up a username

### Build type definition

The core interface is `IBuildTypeDefinition`. Implementations of this
interface (for example `GoPackage`) define the specific configuration
parameters available to the user (e.g., `goVersion`), can define their
default values (e.g., *1.24*), and can perform primary checks on the
provided data in the constructor.

Build type definitions are mapped from the list under `build:` keyword
in the input YAML configuration. Each entry specifies the concrete build
type using the `type` field, which is matched against the available
implementations of `IBuildTypeDefinition`. All remaining fields of the
entry are interpreted as configuration parameters of the selected build
type and deserialised into the corresponding data class.

Apart from type-specific parameters, each build type definition shares a
set of optional common fields:
-   dependencies - define the dependencies and optionally dependency artifacts,
-   triggers - define the triggers,
-   Docker connections - define connections to docker repositories,
-   required free disk space - define the required free space on the runner disk,
-   build number pattern - define pattern for build number.

The common fields represent variables that can be unique to each
pipeline setup.

Each build type entry in the YAML configuration file selects a concrete
build type (an implementation of `IBuildTypeDefinition`) and provides
values for its fields, together with the optional common fields listed
above. This information is deserialised into configuration model classes
and later compiled into build configurations in the pipeline IR.

#### Build type example

The `GoPackage` build type is defined as a Kotlin data class, as
shown below. It demonstrates the use of default values and primary configuration checks.

```kotlin
data class GoPackage(
    val goVersion: String = "1.24",
    val systemList: List<GoSystem> = listOf(GoSystem.linux, GoSystem.windows),
    val archList: List<GoArch> = listOf(GoArch.amd64, GoArch.arm64, GoArch.arm)
) : IBuildTypeDefinition {
    init {
        require(systemList.isNotEmpty()) { "systemList must not be empty" }
        require(archList.isNotEmpty()) { "archList must not be empty" }
    }
}

enum class GoSystem {
    linux,
    windows,
    // ... shortened for example purposes
}
enum class GoArch {
    amd64,
    arm,
    /// ... shortened for example purposes
}
```

This implementation can then be configured from the YAML config as
described in the YAML snippet below. This configuration uses the
default system list - `windows` and `linux` - but overrides the used
version of GoLang and target architectures.

```yaml
yaml build: 
  - type: GoPackage 
    go-version: 1.23
    go-arch: 
      - amd64 
      - arm
```

## Parsing and validation

The input configuration files parsing logic is handled by the class
`InputParser`, which utilises the `jackson` library to deserialise YAML
input into the `InputConfiguration` root object. The input parser
implements a function that accepts a directory containing YAML
configurations. The function walks recursively through the directory and
parses the files into `InputConfiguration`, performing checks for
correct project structure. When the YAML files are parsed, they are then
transformed into a `PipelineSettings` data class, which is the IR of a
component pipeline.

## Pipeline intermediate representation 

The `pipeline` package converts the configuration model into an
executable plan. It provides the data structures that form the
intermediate representation of the pipeline. These structures - such
as `BuildStep`, `Dependency`, `Trigger`, and `Artifact` - map to the
entities defined by the formal model in [FormalModel.md](FormalModel.md).

In the formal model, each build step is associated with a container that
defines its execution environment. The implementation realises the
containerization with Docker.

The component and its pipeline configuration are defined in the class
`PipelineSettings`. It contains a list of `IBuildConfiguration`, which
describes the whole build process. The `IBuildConfiguration` interface
is the central component of the `pipeline` package. While the
configuration model's `IBuildTypeDefinition` defines configuration
parameters, each implementation of `IBuildConfiguration` acts as a
factory that compiles those parameters into a sequence of atomic build
steps.

The architecture separates the configuration schema from the build
logic. While the configuration model captures the user's intent, the
pipeline IR encodes the executable structure derived from that intent. A
developer can introduce a new build type by defining the input
parameters in the implementation of `IBuildTypeDefinition` and
implementing `IBuildConfiguration` to define the build steps and other
entities of a build configuration. This allows the system to grow while
maintaining a platform-independent structure.

For example, a user might configure a build type `GoPackage` with
`go-version: 1.23`. The `configurationModel` stores this value as part
of the build type definition. The corresponding `GoPackage`
implementation of `IBuildConfiguration` reads this parameter and uses it
as a Docker image tag for the Go toolchain in which the packaging script
is executed. The packaging logic itself is fixed by the standard and
does not need to be redefined by the user. See example in
Section [1.7](#sec:goStandartExample)
The class diagram in
Figure below provides a structural overview of the
`pipeline` package entities and their relations.

![PipelinePackage - class diagram.png](resources/PipelinePackage%20-%20class%20diagram.png)

## Build configurations

A build configuration is an implementation of `IBuildConfiguration`. Its
entities map to the formal model. The actual implementations can use
`AbstractBuildConfiguration` implementation, which automatically sets
common fields from the build type definition, such as dependencies.

Each build configuration can set the type to one of:
-   `REGULAR` presents no special behaviour,
-   `COMPOSITE` automatically publishes all dependency artifacts,
-   `DEPLOYMENT` can have additional permissions and rules set up in the
    target platform.

## Core pipeline features

This section describes the core features of the pipeline model that are
shared across all build configurations. It explains how these features
are expressed in the input configuration, represented in the pipeline
IR, and how their semantics follow the formal model introduced in [FormalModel.md](FormalModel.md). These features constitute the mechanisms
through which pipelines express dependencies, artifact flow, execution
conditions, and runtime environment settings.

### Artifacts

This section describes how artifact paths defined in the pipeline
configuration are evaluated and mapped to the artifact semantics of the
formal model.

An artifact source path corresponds to the source path $s$ defined in the formal model and may be specified as:
-   a single file,
-   a directory,
-   a pattern selecting multiple files or directories using wildcards
    \"$*$\" and \"$**$\" [^1].

In all cases, the source path is evaluated recursively. When an artifact
is published or consumed as a dependency, the full relative directory
structure of the source path is preserved.

#### Publish artifacts

The destination path $d$ specifies the target directory in the artifact
repository. If $d$ refers to a compression archive, the files selected
by the source path are recursively copied, preserving their relative
directory structure, and compressed into the archive when published.

#### Dependency artifacts

Dependency artifacts may reference files published and stored in a
compressed archive using notation `<packagePath>!<internalPath>`, where
`packagePath` identifies the published archive location in the artifact
repository and `internalPath` selects a path within that archive. The
selected files are then recursively extracted, preserving their relative
directory structure, into the working directory of the build
configuration.

In the pipeline package, dependency artifacts are implemented as part of
the `Dependency` data class, as they are semantically bound to an
explicit dependency between build configurations. The YAML configuration
of dependency artifacts is described in the following section.

### Dependencies 

Dependencies are declared in the dependent build configuration $x$. A
dependency on a build configuration $a$ is specified by referencing the
FQN of $a$, that is, $DEP(a, x)$ holds if and only if $x$ declares a
dependency on $a$.

Build configuration $x$ can set a failure action on each dependency,
defining the behaviour if the dependency's build configuration execution
fails. This is defined by the enumeration `FailureAction`, which consist
of:
-   `IGNORE` - Proceed as if dependency succeeded; do not fail the
    dependent build
-   `ADD_PROBLEM` - Proceed, but mark a problem in the dependent build
-   `FAIL` - Fail the dependent build immediately if the dependency
    failed or was cancelled cancelled
-   `CANCEL` - Cancel the dependent build if the dependency build
    failed or was cancelled

The code below shows how a dependency is configured
in the input YAML file. In the example, in the first build definition,
the dependency is created to the build configuration named
`BuildConfiguration`, defined by component `::Project::Component` using
its FQN. If the dependency is set to a build configuration defined in
the same component, as in the second build definition, it can be defined
by only the build configuration name, without using FQN. It sets the
failure action to `FAIL`. It uses the `artifacts` keyword to define
*dependency artifacts*

```yaml
build: 
  - type: BuildType # Inserted for context 
    name: "First Build" # Inserted for context 
    dependencies:
      - build-configuration-name: "::Project::Component::BuildConfiguration"
        failure-action: FAIL
        artifacts: 
          - source: "*"
            destination: "artifactsFromRemote/"
  - type: OtherBuildType # Inserted for context
    name: "Second Build" # Inserted for context 
    dependencies: 
      - build-configuration-name: "First Build"
        failure-action: FAIL # Does not set any dependency artifacts
```

The execution of a build chain resolves the work branches used for each
build configuration in the chain. When triggering dependent build
configurations, the system attempts to execute them on the same branch
as the originating build, provided that such a branch exists for the
dependent configuration. If the branch cannot be resolved, the
repository's default branch is used instead.

### Triggers 

The triggers are defined as a common field of a build type `triggers`.
It uses the `type` keyword to select a trigger type, which can be one of
the following:
-   vcs
-   schedule
-   finish-build

The trigger configuration is parsed directly into a concrete
implementation of the `Trigger` class defined in the `pipeline` package.
Each trigger can define `parameters`, `include-branches`, and
`exclude-branches`.

The set of work branches, defined in the formal model in
Section [\[sec:triggers\]](#sec:triggers), is defined as
$\mathit{WB} = \mathit{IB} \setminus \mathit{EB}$, where $\mathit{IB}$
is the set defined by `include-branches` and $\mathit{EB}$ is the set
defined by `exclude-branches`. The branches definition supports \"$*$\"
and \"$**$\" wildcards. By default, the include branches are defined as
all branches and tags available in the version control system.

Example trigger configuration is shown below.

```yaml 
build:
  - type: BuildType # Inserted for context 
    triggers: 
      - type: vcs
        include-branches: 
          - "*"
        exclude-branches: 
          - "develop/*" 
      - type: schedule 
        cron: "0 4 * * *" # Every day at 4:00
        include-branches: 
          - master 
        parameters: 
          - name: PARAMETER_NAME
            value: "overridden value"
```

#### Special branches

The application implements special behaviour for some branches. It
enables setting work branches with prefix `tags/` to work with
repository tags. Then, everything after the prefix is the filter itself,
working in the particular universe - repository tags. For example,
`tags/v*` matches all tags in the repository starting with the letter
\"v\".

### Parameters

To maintain platform independence, the pipeline IR uses a generic syntax
for variable substitution.

For using pipeline parameters in build configuration settings,
placeholders are implemented:
-   `%pipelineGetParam(PARAM_NAME)%`, and
-   `%pipelineSetParam(PARAM_NAME, VALUE)%`

The `pipelineGetParam` placeholder can be used almost anywhere in the
configuration, for example, to use a parameter value in a build step
script or to define a Docker image. The `pipelineSetParam` can be used
in a build step script. The `VALUE` is assigned to the parameter
`PARAM_NAME` after the build step finishes.

The parameter name can be defined as FQN, which allows the use and
override of parameters from dependencies. A parameter value can be
overridden only when it is set as a trigger parameter in a dependent
build, or when the build configuration is triggered manually.

Distinct CI/CD platforms use different parameter names to represent the
same concept, e.g., the build branch, on which the build configuration
is started, is referenced by variable `teamcity.build.branch` in
TeamCity, while in GitLab CI, it is referenced by `CI_COMMIT_REF_NAME`.
For this reason, the pipeline package defines a set of standard
parameters, which are:
-   `PIPELINE_BUILD_BRANCH` - build branch of the build job,
-   `PIPELINE_BUILD_NUMBER` - build number of the build job,
-   `PIPELINE_BUILD_COUNTER` - counter of build configuration
    executions
-   `PIPELINE_DEFAULT_BRANCH` - default branch of the build job owning
    components repository
-   `PIPELINE_COMPONENT_URL` - owning component's repository URL
    address

During platform code generation, these parameter placeholders are
replaced by their platform-specific statements and values by the
parameter replacer [ParameterReplacer.kt](../src/main/kotlin/generator/ParameterReplacer.kt).

### Environment settings 

Each build type can set these environment settings in the input YAML
configuration to pass them to a build
configuration:
-   Docker registry connection
-   Free disk space
-   Build number pattern

A Docker registry connection is essential to pull Docker images from a
repository. It can be set for any build configuration as a common build
type field `docker-connections`.

If *Free disk space* is set, the build job runner checks the available
space on its disk when the build job is started. If sufficient disk
space is not available, the build job fails. It can be set for any build
configuration as a common build type field `free-space-gb`.

The build number helps users identify a concrete build job. The pattern
should use a counter implemented by the target platform. The build
number pattern doesn't serve for referencing build jobs from other build
jobs. The pattern should utilise the standard parameter
`PIPELINE_BUILD_COUNTER` to create unique numbers. It can be set as a
common build type field `build-number-pattern`.

```yaml 
build:
  - type: BuildType # Inserted for context
    docker-connections: 
      - connectionToRegistry # Name of connection
    free-disk-gb: 10 
    build-number-pattern: %pipelineGetParam(PIPELINE_BUILD_COUNTER)%
```

## Standardised build configuration 

This section demonstrates how the pipeline model and configuration model are combined in a concrete, reusable build configuration. 
The `GoPackage` build configuration serves as an example of standardisation.
Golang is not a heavily standardised programming language, but it describes some good practices. 
A BringAuto standard for GoLang is implemented in [GoPackage.kt](../src/main/kotlin/pipeline/buildConfiguration/GoPackage.kt).

-   **Versioning:** It automatically extracts the version number from
    the `version.txt` file.

-   **Build Flags:** It enforces specific compiler flags to ensure
    reproducible, lightweight builds.

-   **Artifact Naming:** It generates a standardised package name
    combining the module name, version, OS, and architecture (e.g.,
    `app_v1.0.0_linux-amd64.zip`).

-   **Packaging:** It bundles the binary with `LICENSE`, `README.md`,
    and resource files into a final archive using a standardised tool.

The implementation of IBuildTypeDefinition, [GoPackage.kt](../src/main/kotlin/configurationModel/buildTypeDefinitions/GoPackage.kt)
contains fields for GoLang version, system list, and architecture list. 
This enables the user to set up a specific Go version for compiling and a set of systems and
architectures for which the application will be built and packaged.

The implementation of IBuildConfiguration utilises the provided values
from the build type definition to define the Docker image tag and create
a build matrix. The values do not change the packaging script, which
adheres to the standard definition.

By using this build configuration, all Go projects in the organisation
produce uniform packages.

[^1]: Wildcard documentation and examples:
<https://www.jetbrains.com/help/teamcity/wildcards.html>
