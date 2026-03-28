# System architecture

The tool developed as part of this thesis is a command-line application
`PipelineCompiler`. This chapter presents the model-driven
software architecture of the tool and explains how its main parts
cooperate to transform an abstract pipeline description into
platform-specific CI/CD pipeline configurations. The architecture is
model-driven in the sense that all processing stages are centred around
a formal, platform-independent pipeline model, which serves as the
semantic foundation of the system.

The chapter introduces the key components of
`PipelineCompiler`, their responsibilities and interactions,
and shows how the architecture reflects the formal pipeline model
defined in the previous chapter. The overall structure follows a
three-stage compiler design:

-   parsing input configurations (frontend),

-   compiling them into an *Intermediate Representation* (IR) derived
    from the formal model (mid-end),

-   and generating platform-specific configurations (backend).

The figure below illustrates the three main
packages of the implementation, where each package reflects one compiler
stage. The following chapters then describe each package implementation
in detail.

![System architecture
overview.](resources/architectureOverview.png)

## Design goals

The design is based on three main requirements: standardisation,
extensibility, and abstraction.

### Standardization

The standardisation requirement defines the need to create standardised
CI/CD processes, such as application build, packaging, testing, and
release, and connect them into build chains. The standardisation creator
(e.g., organisation, tool) can put requirements on different aspects,
such as the project directory structure, output package naming, and
versioning.

These standards are reflected in predefined build configurations. This
enables the user to use predefined types, which encapsulate the
organisation's best practices, instead of manually writing a script for
each CI/CD process.

Each predefined build type is based on a standard. Some programming
languages come with their own standard, but for others, it must be
artificially created. Organisations often define their own standard for
different programming languages.

Predefined build configurations, their creation and usage are further
described in
Chapter [\[ch:parsingConfiguration\]](#ch:parsingConfiguration){reference-type="ref"
reference="ch:parsingConfiguration"}

### Extensibility

There are two main dimensions of extensibility on which the
`PipelineCompiler` focuses.

The first dimension is the ability to support new CI/CD platforms.
The Intermediate Representation contains all necessary information to consistently recreate the pipeline
on different platforms. To support a new target platform, the system
needs to implement a new generator which maps the Intermediate
Representation entities to the specific syntax of the CI/CD system
(e.g., GitHub Actions or Jenkins).

The second dimension involves adding support for new CI/CD processes by
introducing predefined build configurations, as mentioned in the
standardisation section above.

### Abstraction

Abstraction aims to hide the specifics of individual CI/CD platforms
from the user, while enabling them to define the build process for their
application and specify the dependencies between individual build
configurations and components.

By utilising the predefined build configurations and dependencies, the
user describes *what* should happen, while the compiler is responsible
for translating this description into the concrete syntax and
conventions of the target CI/CD system. This reduces the learning effort
required for new users and lowers the cognitive overhead when extending
pipelines or migrating across multiple platforms.

## Used technologies

This section provides the rationale behind the technologies used by the
`PipelineCompiler`.

### Programming language

The `PipelineCompiler` is implemented in *Kotlin*. The
selection is motivated by these reasons:

-   **Object-oriented, statically typed language.** Kotlin supports
    interfaces, inheritance, and sealed hierarchies, which makes it
    natural to model extension points, such as new build types or CI/CD
    platforms, as new implementations of existing interfaces.

-   **Serialisation support.** Data classes and mature libraries enable
    the direct serialisation and deserialisation of configuration data
    to strongly typed objects.

-   **Code generation support.** Libraries such as `KotlinPoet` make it
    straightforward to generate type-safe Kotlin code for TeamCity DSL.

### Input configuration

The input configuration is unified across all supported CI/CD platforms
and is designed to describe a pipeline in a platform-independent manner.
One configuration yields pipeline settings with comparable behaviour for
each target platform.

To support the design goal of reducing the learning curve, the
`PipelineCompiler` uses an existing, widely adopted
configuration format instead of introducing a new domain‑specific
language. The pipeline is configured using YAML files. This choice is
motivated by the following properties:

-   **Popularity in CI/CD.** YAML is the most commonly used syntax for
    expressing pipeline configurations among CI/CD platforms (e.g.,
    GitLab CI, GitHub Actions).

-   **Readability.** YAML is comparatively human‑readable, which makes
    pipeline definitions easier to write and review than formats such as
    JSON or XML.

-   **Support for comments.** YAML allows inline comments, which are
    useful for documenting individual configuration decisions directly
    in the configuration file.

The detailed schema of the YAML configuration and its mapping to the
internal configuration model is described in TODO reference

## High-level overview

The `PipelineCompiler` follows a standard compiler
architecture composed of three main phases: parsing, intermediate
representation, and generation. These phases correspond to the three
main packages of the application. The high-level architecture is shown
in image above. 

The `configurationModel` package serves as the parser, mapping input
YAML files to Kotlin data objects. These data objects are then
transformed into an *Intermediate Representation* (IR) of a CI/CD
pipeline, defined in the package `pipeline`. This IR is a direct code
realisation of the formal model described in [FormalDefinitions.md](FormalModel.md).
Finally, the IR is processed by the `generator` package, which implements the target platform code
generators and produces platform-specific pipeline configuration files that can be used on the platform.

The `common` package supports these packages. This package is not shown
in the diagram, as it does not directly map to any stage of the
compiler. It provides utilities such as `ProjectPath` for unique entity
identification and `DiffAndBackup` for managing file updates safely and
aiming to improve the user experience.
