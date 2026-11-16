# Parameters

Using parameters allows the user to customise the behaviour of pipelines.

Parameters can be used as:
* Input: to customise the behaviour of the pipeline before it starts. This can be user input, environment variables, or other sources.
* Output: to expose values to succeeding steps.

Parameter name must follow the regex pattern: `[a-zA-Z_][a-zA-Z0-9._]*`
> Note: 
> Different platforms may have different constraints. This regex is set according to rules for shell variables.
> TeamCity requires dots in parameter names. Therefore, it's added to the regex.

## Defining parameters

Parameter types are defined in [Parameter Types](../src/main/kotlin/pipeline/Parameter.kt).
Parameters can be defined directly in the pipeline build configuration, or they can be passed from the input configuration.
Parameters are defined under the `parameters` key of a build step (supported by `script-runner` and `composite` types).

Each parameter entry requires `name` and `value`. The `type` field selects the parameter kind:

| type | Description | Extra fields |
|---|---|---|
| `parameter` | Plain key/value, no UI metadata | — |
| `text` | Free-text input field | `label`, `description`, `display-type` |
| `select` | Dropdown with fixed choices | `label`, `description`, `options`, `allow-multiple`, `display-type` |
| `secret` | Masked/secret value | `label`, `description`, `display-type` |

`display-type` controls UI visibility: `NORMAL` (default), `PROMPT`, or `HIDDEN`.

```yaml
parameters:
  - name: MY_PARAM
    value: default
    type: text
    label: My Parameter
    description: A short description shown in the UI

  - name: RELEASE_TYPE
    value: patch
    type: select
    label: Release type
    options:
      - patch
      - minor
      - major

  - name: API_TOKEN
    value: ""
    type: secret
```

## Using parameters

Only previously defined parameters can be used in the build configuration.
The compiler provides special placeholders described below to get/set parameter values in the pipeline settings.
The placeholders are replaced with the platform-specific code to get/set the parameter value.

> Note:
> This is used to specify the pipeline parameters.
> Variables defined in the script content can be used normally in the cript as in any script.

### Get parameter value

To use a parameter value, use the `%fanciGetParam(<parameter name>)%` clause.
- May be used in any place where a value is expected.
- Cannot be used as a parameter name/key (only within values).
- Supports fully qualified names (FQN) to reference parameters from other build configurations; see [Fully qualified name](BuildChaining.md#concepts).

Examples:
- Local: `%fanciGetParam(my_param)%`
- FQN: `%fanciGetParam(::Project::Component::Build::my_param)%`


### Set parameter value

To set a parameter value, use the `%fanciSetParam(<parameter name>, <value>)%` clause.
- Only valid in script content.
 
Example:
- In a script: `%fanciSetParam(IMAGE_TAG, 1.2.3)%`

## Using dependencies parameters

Parameters from dependencies can be used or overridden in a build configuration.

To work with dependency parameters:
- The dependency must be defined in the pipeline
- Use a fully qualified name of the parameter. See [Fully qualified name](BuildChaining.md#concepts)

> NOTE (TeamCity current behaviour)
> Currently, in TeamCity, you must use the full ID of the project - if the generated pipeline is nested in another project, this project must also be part of the fully qualified name.
> This will be reworked once support for GitLab is added.

### Using parameters from preceding build configurations

Use `%fanciGetParam(<parameterFullyQualifiedName>)%`.

### Overriding input parameters

It is possible to override input parameters in preceding build configurations of the chain using the *Fully qualified name of the parameter*.

This can only be done in the `parameters.name` section - set parameters before the chain is started.
This includes also parameters in `triggers` sections -> `triggers[n].parameters[n].name`. 


## Standard Platform Parameters

Different CI/CD platforms often use unique variable names to represent the same concepts.
For instance, the current build branch is referenced as `teamcity.build.branch` in TeamCity, whereas GitLab uses `CI_COMMIT_REF_NAME`.

To ensure pipeline configurations remain platform-agnostic, the generator uses a set of **Standard Parameters**.
These generic placeholders are automatically replaced with the correct platform-specific variable during generation.

### Naming Convention
All standard parameters follow the `PIPELINE_` prefix convention (e.g., `FANCI_BUILD_BRANCH`).

### Implementation Details
*   **Source of Truth:** The complete list of required parameters is defined in the `StandardParameter` enum within [IPlatform.kt](../src/main/kotlin/generator/IPlatform.kt).
*   **Mapping:** Each platform generator (e.g., GitLab, TeamCity) defines a `platformParameters` map that translates these standard keys into the specific environment variables required by that platform.


## Examples

### Teamcity

In TeamCity, the placeholders are replaced with these:
- Get parameter value: `%parameterName%`
- Set parameter value: `echo "##teamcity[setParameter name='parameterName' value='newValue']"`
- Get parameter from dependency: `%dep.dependencyPath.parameterName%`
- Override dependency parameter: `param(reverse.dep.dependencyPath.parameterName, value)`

Note: Other platforms will use different syntaxes; the placeholders abstract these differences.

