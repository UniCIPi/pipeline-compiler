# Pull requests

A pull request (a merge request in GitLab terminology) is a request to merge a *source* branch into a *target* branch.
The generator can build pull requests on both target platforms, so that a change is verified before it is merged.

Pull request support is **enabled for every component by default** and covers **all** pull requests.
The optional `component.pull-request` section only narrows the default or turns the support off.

Two things are needed to build a pull request:

1. The component must have pull request support (this document).
2. A trigger of the build configuration must accept pull request branches, either explicitly with the `mr/` branch
   pattern or implicitly with a trigger that does not restrict branches. See [Triggers.md](Triggers.md).

## Configuration

```yaml
component:
  name: "::Project::Component"
  repository: "https://gitlab.example.com/group/repository.git"
  vcs-auth-method:
    type: token
    token-id: "credentialsJSON:vcs-token"
  submodule-strategy: none
  pull-request:
    enabled: true                              # optional; default true
    token-id: "credentialsJSON:pr-token"       # optional; default: credentials of the VCS root
    include-source-branches:                   # optional; default: all source branches
      - feature/*
    exclude-source-branches:                   # optional
      - wip/*
    include-target-branches:                   # optional; default: all target branches
      - main
      - release/*
    exclude-target-branches:                   # optional
      - legacy
  build-configurations: []
```

Fields:
- `enabled` (boolean, optional, default `true`): When false, no pull request support is generated for the component
  and `mr/` trigger branches are ignored.
- `token-id` (string, optional): Token used to read pull requests from the VCS host system API. See [Authentication](#authentication).
- `include-source-branches` / `exclude-source-branches` (list of strings, optional): Filter on the branch the pull
  request originates from. Supports wildcards. An empty include list means all source branches.
- `include-target-branches` / `exclude-target-branches` (list of strings, optional): Filter on the branch the pull
  request is merged into. Supports wildcards. An empty include list means all target branches.

The branch filters are matched against the source and target branch of the pull request, **not** against the pull
request branch itself. Which build configurations run for a pull request is decided by their triggers.

## Authentication

Reading pull requests requires access to the API of the VCS host system.

- When the component `vcs-auth-method` is `token` or `refreshable-token`, the **same token is reused** and no
  additional configuration is needed.
- For any other authentication method (`uploaded-key`, or no authentication at all), the credentials of the VCS root
  cannot be reused. The generator emits a warning and the token has to be configured in `pull-request.token-id`.
- When `pull-request.token-id` is set, it takes precedence over the VCS root credentials.

`token-id` supports the `%fanciGetParam(<parameter name>)%` clause, the same as the token of `vcs-auth-method`.
The placeholder is resolved to the target platform format by the parameter replacer, so the token can be kept in a
platform parameter instead of the configuration:

```yaml
pull-request:
  token-id: "%fanciGetParam(PR_ACCESS_TOKEN)%"
```

See [Parameters.md](Parameters.md) for details.

## Pull request branches in triggers

Pull requests are exposed under a branch whose name differs per VCS host system. The generator therefore uses the
platform-independent `mr/` prefix, analogous to the `tags/` prefix, and translates it during generation:

| VCS host system | Reference                     | Translated branch  |
|---|---|---|
| GitLab | `refs/merge-requests/<id>/head` | `merge-requests/*` |
| GitHub | `refs/pull/<id>/head`           | `pull/*`           |
| Gitea  | `refs/pull/<id>/head`           | `pull/*`           |

```yaml
triggers:
  - type: vcs
    include-branches:
      - main
      - "mr/*"    # every pull request
```

`mr/*` covers all pull requests. A concrete pull request can be selected by its id, e.g. `mr/42`.
The pattern can be used in `exclude-branches` as well, to keep a build configuration out of pull request builds.

A trigger that does not restrict branches at all builds every branch **and** every pull request.

## Platform implementation

### TeamCity

A [pull requests build feature](https://www.jetbrains.com/help/teamcity/pull-requests.html) is generated for every
build configuration of the component. The feature attaches to the VCS root of the component and makes TeamCity
monitor pull requests, exposing each of them as a logical branch that triggers can match with their branch filter.

```kotlin
features {
    pullRequests {
        vcsRootExtId = "${Project_Component_VCS.id}"
        provider = gitlab {
            authType = vcsRoot()
            filterSourceBranch = """
                +:*
                -:refs/heads/wip/*
            """.trimIndent()
            filterTargetBranch = "+:refs/heads/main"
        }
    }
}
```

Notes:
- TeamCity pull request branch filters accept **fully qualified VCS references only**. Branch names from the
  configuration are therefore qualified to `refs/heads/<branch>`; a value that already starts with `refs/` is kept.
- TeamCity implements pull request providers for GitLab and GitHub only. For any other host system the feature is not
  generated and a warning is emitted.

### GitLab

GitLab needs no build feature; a merge request pipeline is recognized by the `merge_request_event` pipeline source.
The generator adds `rules` only:

```yaml
MyBuild:
  rules:
  - if: $CI_PIPELINE_SOURCE == 'push' && $CI_COMMIT_BRANCH == 'main'
    when: on_success
  - if: $CI_PIPELINE_SOURCE == 'merge_request_event' && $CI_MERGE_REQUEST_SOURCE_BRANCH_NAME !~ /^(wip\/.*)$/ && $CI_MERGE_REQUEST_TARGET_BRANCH_NAME == 'main'
    when: on_success
```

The branch filters of the `pull-request` section become conditions on the GitLab predefined variables
`CI_MERGE_REQUEST_SOURCE_BRANCH_NAME` and `CI_MERGE_REQUEST_TARGET_BRANCH_NAME`. A concrete `mr/<id>` pattern becomes
a condition on `CI_MERGE_REQUEST_IID`.

Note that GitLab creates a branch pipeline **and** a merge request pipeline for a push to a branch with an open merge
request. The generator does not create a `workflow` section, so suppressing the duplicate pipeline is left to the
repository, for example:

```yaml
workflow:
  rules:
    - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
      when: never
    - when: always
```
