# To be done

## General

### Build Configuration

#### Dependencies

Can we add some sort of reusing artifacts from previous builds of dependencies - dont rebuild every time? (in TC it seems to be working like that already, but not sure)

Automatic build chain
- Maybe we can create a build chain automatically based on the order of steps defined in `Build` section.
- It's not necessary, as we can define the build chain manually. Check use-case if there is any example denying this


#### Build Steps

See if there is use-case for:
- Run step on condition only (e.g. if previous step failed)

### Features

Add mail notification feature. The email must be set for each build configuration

### Build chain - dependencies

Different action for cancel and failure?

Should always synchronize revisions?

additional TeamCity functions:
  - reuseBuilds,

### TeamCity env

SshKeys can be part of project. Rather than using repositoryContains, it should walk key by key and find the most suitable one.

SSH agents should not be automatically added for security reasons. There should be a possibility to add SSH agent manually in the build configuration.

## Gitlab

#### Docker registries

The Docker registry ID is name of variable storing the registry URL. For username and password it will append to it. This will be required format.

## Could be nice

- BringAuto specific CMake build step - automatic CMake options