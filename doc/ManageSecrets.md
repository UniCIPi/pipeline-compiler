# Managing secrets

This document describes how to manage secrets.

## General rules

The generator shall always work with secret names only - not with secret values.

Platforms usually provide a way to get the secret value by its name, therefore the secrets are not necessary in the generator.

## Connections and authentication

Connections must be set up in the CI/CD platform. The most important connections are to:
-   VCS repository,
-   Docker registry.

In both platforms, the connection is set up in the platform settings and provides a reference mechanism. 
The configuration never requires providing any secret value.

### GitLab

GitLab doesn't support fine-grained control over Docker registry
connections. Instead, each component can use the content of a Docker
configuration file, which contains connections, to define the variable
`DOCKER_AUTH_CONFIG`. Each build configuration has access to all Docker
registries that are defined in this `config.json`.

GitLab supports only components that are hosted on the same server.
Therefore, no additional VCS connections are needed.

### TeamCity

Docker registries are set up in the Connections section of a project's
settings. The created connection is named, and this name is used in
`docker-connections:` sections of the configuration YAML.

The VCS connection method can be either:
-   uploaded key --- upload SSH key to project and use its name
-   token --- generate an authentication token and use its ID

## Environment file

This section describes platform-specific environment files. The `generatePipeline` function can accept a path to a file, 
which is used to define platform-specific values. 
These values vary from platform to platform and can differ for each deployment. 
Therefore, they cannot be a part of the platform-independent model.

In GitLab, the environment file is used to define runner tags. 
It can define one default runner tag and one for the Docker-in-Docker runner,
where it can also specify the Docker host address and TLS certificate directory.
In TeamCity, the environment file is used to define connection names.
The generator then checks if the used connection name exists in the environment file.

In TeamCity, the environment file can define the configured Docker registry connection names. 
The generator then checks if the Docker connection specified in the input configuration is present in these defined connections.

### Platform specifics

#### TeamCity

All authentication secret names used in configuration must be defined in the environment file.
This serves as a double check to make sure authentication will work as expected. See [TeamCityEnvironment.kt](../src/main/kotlin/generator/platforms/teamcity/TeamCityEnvironment.kt)

> The environment file functionality can be expanded, right now it is a proof of concept.

##### Example env file

```json
{
  "sshKeys": [
    {
      "name": "universal-key"
    }
  ],
  "dockerRegistries": [
    {
      "name": "DockerHub_Connection"
    },
    {
      "name": "GCloudArtifactRegistry_Connection"
    }
  ]
}
```
##### VCS key

If a key is defined in settings, it must be defined in the environment file as well.
If not, first key without `repositoryContains` will be used, as this one should be a universal key.

The following command gets all SSH keys available for specified project - its own and from all parent projects:
```bash
curl -s -u <username>:<password> "localhost:8111/app/rest/projects/<project>/sshKeys" \
  -H "Accept: application/json" 
```

##### Docker registries

Docker registries are docker connection IDs. Docker connection is set up in Project settings in TeamCity.

##### Tokens
It is possible to get all Versioned settings tokens with the following command:

```bash
curl -s -u <username>:<password> "<server-address>/app/rest/projects/_root/versionedSettings/tokens" \
  -H "Accept: application/json"
```

This provides a list of all tokens in the Versioned settings, which can be used to manage secrets.


### GitLab

Set up GitLab CI/CD runner tags in the project settings. Additionally set up additional information for dind connection.

See [GitLabEnvironment.kt](../src/main/kotlin/generator/platforms/gitlab/GitLabEnvironment.kt)

