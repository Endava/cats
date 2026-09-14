---
sidebar_position: 1
description: All CATS sub-commands
---

# Sub-Commands

To list all available commands, run:

```bash
cats -h
```

All available subcommands are listed below:

- `cats help` or `cats -h` will list all available arguments and sub-commands

- `cats list --fuzzers` will list all the existing fuzzers, grouped by categories

- `cats list --profiles` will list the built-in fuzzer profiles and the fuzzers selected by each profile

- `cats list --mutators` will list the mutators available to `cats random`

- `cats list --fieldsFuzzerStrategies` will list the field-fuzzing strategies

- `cats list --customMutatorTypes` will list the supported custom mutator types

- `cats list --paths --contract=CONTRACT` will list all the paths available within the contract

- `cats list --paths --contract=CONTRACT --path /my-path` will list some details about the given `/my-path`

- `cats list --formats` will list all supported formats for fields

- `cats replay "test1,test2"` will replay the given tests `test1` and `test2`

- `cats template` will fuzz based on a given request template, rather than an OpenAPI contract

- `cats run` will run functional and targeted security tests written in the CATS YAML format

- `cats lint` will run OpenAPI contract linters, also called `Linter` Fuzzers

- `cats info` will print debug information that are useful when submitting bug reports

- `cats stats` will display basic statistics about a given OpenAPI contract like number of paths, operations, versioning, etc.

- `cats validate` checks if an OpenAPI spec is valid and version used

- `cats random` does continuous fuzzing based on mutators until a certain stop condition is hit

- `cats explain` will explain a given response code or reason message

- `cats generate` will generate a request based on a given OpenAPI contract

- `cats generate-completion` will generate a bash/zsh completion script for `cats`. Enable it in the current shell with `source <(cats generate-completion)`.

The `cats explain --type TYPE INFO` command provides detailed information about a
fuzzer, mutator, response code, or error reason. For example:

```bash
cats explain --type response_code 953
cats explain --type error_reason "Error details leak"
```

:::tip
Each sub-command has its own help. You can run `cats sub-command -h` to check all available arguments: `cats template -h`.
:::
