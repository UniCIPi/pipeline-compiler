# Triggers

This document describes how to define triggers in the pipeline configuration. Triggers control when a build configuration starts.

Supported triggers in the current model:
- VCS trigger (`type: "vcs"`)
- Schedule trigger (`type: "schedule"`)
- Finish Build trigger (`type: "finish-build"`)

General rules:
- The `type` field is required. Omitting it will fail deserialization.
- Common fields available for all triggers:
  - `parameters` (list, optional): Parameters to set/override when the build is started by this trigger. Same structure as [build configuration parameters](Parameters.md).
  - `include-branches` (list of strings, optional): Branch patterns to include. If not include-branches are provided, all branches are included by default.
  - `exclude-branches` (list of strings, optional): Branch patterns to exclude.

The trigger works on a set of work branches. $WorkBranches = IncludeBranches \setminus ExcludeBranches$
The exact interpretation of work branches is trigger-type dependent. The trigger types are described below.

**Special values:**

To define tags in *include-branches* or *exclude-branches*, use the `tags/` prefix. 
The generator will translate `tags/` into platform-specific clauses.

## VCS Trigger

Starts a build when changes are detected in VCS. You can optionally scope the trigger to specific VCS roots and paths via `trigger-rules` and also filter by branches via `include-branches` / `exclude-branches`.

Supports triggering on changes in VCS roots of other components.

Fields (VCS):
- `trigger-rules` (list): Optional list of objects specifying path-based rules per VCS root. Each rule supports:
    - `vcs-root-component` (string, optional): Name of the component, defining VCS root to attach the rule to.
    - `include-paths` (list of strings): Paths to include. 
      - Supports Ant-like wildcards. 
      - At least one of `include-paths` or `exclude-paths` must be provided.
      - If no `include-paths` are provided, all files are included automatically.`
      - If `vcs-root-component` is defined the `include-paths` must not be empty. 
        - This is for deterministic reasons. Otherwise, TeamCity would fire the trigger on all changes in all dependencies.
    - `exclude-paths` (list of strings, optional): Paths to exclude. Supports Ant-like wildcards.
- `<common fields>`

Minimal YAML. This triggers on every changes:
```yaml
type: vcs
```

Advanced YAML with trigger rules:
```yaml
type: vcs
trigger-rules:
  - vcs-root-component: ::Project::ComponentA        # optional; attaches rule to a specific VCS root
    user: JohnDoe             # optional; author filter
    include-paths:        # at least one of include-paths/exclude-paths must be non-empty
      - src/**
      - tests/**
    exclude-paths:
      - src/generated/**
include-branches:
  - main
  - develop
exclude-branches:
  - release/*
parameters:
  - name: runType
    value: commit
```

Work branches limits, which branches are watched for changes.

## Schedule Trigger

Starts a build on a schedule using a UNIX cron expression.

Unix cron expression fields:
- Minute (0-59)
- Hour (0-23)
- Day of month (1-31)
- Month 
  - 1-12, where 1 is January, 2 is February, and so on.
  - Uppercase, lowercase and mixed-case three character strings, based on the English name of the month. For example: jan, feb, mar, apr, may, jun, jul, aug, sep, oct, nov, or dec.
- Day of week
  - 0-7, where 0 or 7 is Sunday, 1 is Monday, and so on.
  - Uppercase, and lowercase or mixed-case three character strings, based on the English name of the day: mon, tue, wed, thu, fri, sat, or sun.


YAML:
```yaml
type: schedule
cron: "0 0 * * *"                 # every day at midnight
with-pending-changes-only: false  # optional; default false
include-branches:
  - main                          # run build only on branch `main`
parameters:
  - name: runType
    value: nightly
```

Fields (Schedule):
- `cron` (string, required): Standard 5-field UNIX cron syntax `min hour day month weekday`. Quotes recommended.
- `with-pending-changes-only` (boolean, optional): If true, only triggers when there are pending changes.
- `<common fields>`

Work branches define all branches that are started by the trigger.

Notes:
- The trigger starts build for every matching branch - set `include-branches` to limit the scope.
- Timezone and exact semantics are platform/server-specific; ensure the expression matches your server settings.
- GitLab has limited support for schedule creation from code; you may need to configure schedules in the UI.

## Finish Build Trigger

Starts the build configuration after build configuration `X` finishes. Build configuration `X` is referenced by its fully qualified name in `build-configuration-name`.

YAML:
```yaml
type: finish-build
build-configuration-name: ::Project::ComponentA::Composite
successful-only: true  # optional; default false
```

Fields (Finish Build):
- `build-configuration-name` (string, required): Fully qualified triggering build name, e.g. `::Project::ComponentA::Composite`.
- `successful-only` (boolean, optional): Trigger only on successful completion of the specified build.
- `<common fields>`

Work branches define the branches of `X`, that fires the trigger, when ran to completion.
