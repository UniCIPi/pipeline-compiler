# Component

Each input YAML file defines a component.

fields:
```yaml
component:
  name: "::Project::ComponentName"
  repository: "https://provider/repository.git"
  default-branch: <branch-name>
  vcs-auth-method:
    type: <vcs-auth-method-type> # see VCS authentication section below
  submodule-strategy: none # Optional: recursive, shallow, none (default)
  pull-request: # See [PullRequests.md]; optional, pull requests are supported by default
  build-configurations: [] # See [BuildTypes.md]
```

## Pull requests

Building of pull requests (merge requests) is enabled for every component by default and covers all pull requests.
The optional `pull-request` section narrows the support to selected source/target branches, configures the token used
to read pull requests, or turns the support off.

See [PullRequests.md](PullRequests.md) for details.

## VCS authentication

The component configuration specifies authentication details required to access the VCS repository. 
Authentication references secrets defined in a target platform.

The authentication method structures are defined in [VcsAuthMethod.kt](../src/main/kotlin/pipeline/VcsAuthMethod.kt)

The following authentication methods are supported. The method type is selected by a `type` keyword:
- `uploaded-key` - references a named SSH key set up in the platform and can set up a username,
- `refreshable-token` - references a refreshable token defined in the target platform and can set up a username
- `token` - references an authentication token defined in the target platform and can set up a username

### Uploaded key
SSH authentication using an uploaded key stored in the platform under `name`.
If the key is passphrase-protected, set `passphrase` accordingly using SECRET parameter. Never provide secrets in plain text.

```yaml
vcs-auth-method:
  type: uploaded-key
  name: <key-name>
  passphrase: [<optional-passphrase>]
```

### Refreshable token
Authentication using a refreshable token stored in the target platform with `token-id`.
```yaml
vcs-auth-method:
  type: refreshable-token
  token-id: <token-id>
  username: <optional-username>
```

### Token
Authentication using an authentication token/password stored in the target platform with `token-id`. `username` may be required by some platforms.
```yaml
vcs-auth-method:
  type: token
  token-id: <token-id>
  username: [<optional-username>]
