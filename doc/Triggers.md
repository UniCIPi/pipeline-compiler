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

To define pull requests (merge requests) in *include-branches* or *exclude-branches*, use the `mr/` prefix.
The generator will translate `mr/` into the branch naming of the VCS host system of the component
(`merge-requests/` for GitLab, `pull/` for GitHub and Gitea) or into platform-specific clauses.
`mr/*` covers all pull requests, `mr/42` covers the pull request with id 42.

A trigger that defines no branches at all works on all branches **and** all pull requests.

See [PullRequests.md](PullRequests.md) for the component-level configuration of pull request support.

## VCS Trigger

Starts a build when changes are detected in the repository of one component. You can optionally scope the trigger
to the repository of another component via `watched-component`, to changed paths via `include-paths` /
`exclude-paths`, and also filter by branches via `include-branches` / `exclude-branches`.

Fields (VCS):
- `watched-component` (string, optional): Fully qualified name of the component whose repository is watched.
  If not provided, the repository of the component owning the build configuration is watched.
  - To watch several components, define one VCS trigger per component.
  - TeamCity sees the changes of another component only when the component is a dependency of the build
    configuration.
  - GitLab cannot watch other repositories. The paths are applied to the own repository.
- `include-paths` (list of strings, optional): Paths to include. Supports Ant-like wildcards.
  - If no `include-paths` are provided, all paths are included.
- `exclude-paths` (list of strings, optional): Paths to exclude. Supports Ant-like wildcards.
- `commit-message-regex` (string, optional): Java regex applied to the commit message. When set, it is combined
  **conjunctively** with every include and exclude path — a path only matches when the commit message also
  matches.
  - Example: `exclude-paths: ["**"]` + `commit-message-regex: \[skip ci\]` generates `-:comment=\[skip ci\]:**`,
    skipping the build when the commit message contains `[skip ci]`.
- `<common fields>`

Minimal YAML. This triggers on every change in the own repository:
```yaml
type: vcs
```

Advanced YAML:
```yaml
type: vcs
watched-component: ::Project::ComponentA  # optional; watches the repository of ComponentA
include-paths:                            # optional; all paths when empty
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
name: nightly                     # optional; unique within the build configuration
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
- `name` (string, optional): Name of the trigger, unique within the build configuration. On GitLab, it identifies
  the pipeline schedule and protects the schedule from being disconnected when the triggers are reordered
  (see the GitLab note below). On other platforms it is used for identification only.
- `with-pending-changes-only` (boolean, optional): If true, only triggers when there are pending changes.
- `<common fields>`

Work branches define all branches that are started by the trigger.

Notes:
- The trigger starts build for every matching branch - set `include-branches` to limit the scope.
- Timezone and exact semantics are platform/server-specific; ensure the expression matches your server settings.
- GitLab schedules cannot be created from the pipeline definition; they are configured in the UI
  (Build -> Pipeline schedules). The generator prints the schedules to create. Each schedule has to set the variable
  `__PIPELINE_GENERATOR_SCHEDULE` to the identifier of its trigger, so it starts only its build configuration
  and the dependencies of it:
  - `<build configuration>-<name>` for a named trigger,
  - `<build configuration>-<n>` for the n-th unnamed schedule trigger of the build configuration. Reordering
    the unnamed triggers changes their identifiers, the schedules in GitLab then have to be updated.

  The target branch is given by the schedule itself; create one schedule per branch in `include-branches`.
  A schedule without the variable starts no build configuration.

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
