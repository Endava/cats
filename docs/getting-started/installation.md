---
sidebar_position: 1
description: How to get CATS up and running
---
# Installation

## Homebrew

```bash
> brew tap endava/tap
> brew install cats
```

## Manual
CATS is bundled both as an uberjar JAR or a native binary. The native binaries do not need Java installed.

After downloading your OS native binary, you can add it to PATH so that you can execute it as any other command line tool:

```bash
sudo cp cats /usr/local/bin/cats
```

You can also run CATS using the uberjar. This requires Java 17+ to be installed.

You can run it as `java -jar cats.jar`.

:::tip
There is no native binary for Windows, but you can use the uberjar version.
:::

Head to the releases page to download the latest version: [https://github.com/Endava/cats/releases](https://github.com/Endava/cats/releases).

## Autocomplete

You can also get autocomplete in `bash` or `zsh` by downloading the [cats_autocomplete](https://github.com/Endava/cats/blob/master/cats_autocomplete) script and do:

```bash
source cats_autocomplete
```

To get persistent autocomplete, add the above line in `.zshrc` or `.bashrc`, but make sure you put the fully qualified path for the `cats_autocomplete` script.

:::tip
You can also check the `cats_autocomplete` source for alternative setup.
:::

## Build from sources

You can build CATS from sources on you local box. You need Java 17+. Maven is already bundled.

**Before running the first build, please make sure you do a `./mvnw clean`. CATS uses a fork ok `OKHttpClient` which will install locally
under the `4.10.0-CATS` version, so don't worry about overriding the official versions.**

You can use the following Maven command to build the project as an uberjar:

`./mvnw package -Dquarkus.package.type=uber-jar`


You will end up with a `cats-runner.jar` in the `target` folder. You can run it wih `java -jar cats-runner.jar ...`.

You can also build native images using a GraalVM Java version.

`./mvnw package -Pnative`

**Note:** You will need to configure Maven with a [Github PAT](https://docs.github.com/en/free-pro-team@latest/packages/guides/configuring-apache-maven-for-use-with-github-packages) with `read-packages` scope to get some dependencies for the build.

### Notes on Unit Tests

You may see some `ERROR` log messages while running the Unit Tests. Those are expected behaviour for testing the negative scenarios of the `Fuzzers`.

