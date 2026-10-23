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

- `cats list --paths --contract=CONTRACT --executionOrder` (or `--dependencyOrder`) will list the contract paths and operations in the producer-to-consumer execution order computed by CATS

- `cats list --paths --contract=CONTRACT --path /my-path` will list some details about the given `/my-path`

- `cats list --formats` will list all supported formats for fields

- `cats replay "test1,test2"` will replay the given tests `test1` and `test2` (use `--curl` to print equivalent `curl` commands without sending HTTP calls)

- `cats template` will fuzz based on a given request template, rather than an OpenAPI contract

- `cats run` will run functional and targeted security tests written in the CATS YAML format

- `cats lint` will run OpenAPI contract linters, also called `Linter` Fuzzers

- `cats info` will print debug information that are useful when submitting bug reports

- `cats stats` will display basic statistics about a given OpenAPI contract like number of paths, operations, versioning, etc.

- `cats validate` checks if an OpenAPI spec is valid and version used

- `cats random` does continuous fuzzing based on mutators until a certain stop condition is hit

- `cats analyze` (alias `blueprint`) inspects an OpenAPI contract offline and prints its resource dependency graph, producer-consumer chains, orphan path parameters, and a `0-100` Fuzzability Readiness Score (use `--mermaid` to include a Mermaid.js flowchart)

- `cats init` inspects an OpenAPI contract and scaffolds starter `headers.yml`, `refData.yml` (pre-populated with unresolved path parameters), and `functionalTests.yml` files

- `cats smoke` (alias `probe`) runs a fast 1-request-per-operation health, route, and authentication check against a live server using `HappyPathFuzzer` (skips `DELETE` by default unless `--includeDelete` is passed)

- `cats compare` (alias `diff`) compares two `cats-report` folders or `cats-summary-report.json` files apples-to-apples across their shared `(path, httpMethod, fuzzer)` scope for regressions and fixes

- `cats explain` will explain a given fuzzer, linter, profile, mutator, response code, or error reason

- `cats generate` will generate a request based on a given OpenAPI contract

- `cats generate-completion` will generate a bash/zsh completion script for `cats`. Enable it in the current shell with `source <(cats generate-completion)`.

The `cats explain --type TYPE INFO` command provides detailed information about a
fuzzer, linter, profile, mutator, response code, or error reason. For example:

```bash
cats explain --type response_code 953
cats explain --type error_reason "Error details leak"
cats explain --type profile security
cats explain --type linter PathCase
```

:::tip
Each sub-command has its own help. You can run `cats sub-command -h` to check all available arguments: `cats template -h`.
:::
