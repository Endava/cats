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

- `cats list --fieldsFuzzingStrategy` will list all the available fields fuzzing strategies

- `cats list --paths --contract=CONTRACT` will list all the paths available within the contract

- `cats list --formats` will list all supported formats for fields

- `cats replay "test1,test2"` will replay the given tests `test1` and `test2`

- `cats fuzz` will fuzz based on a given request template, rather than an OpenAPI contract

- `cats run` will run functional and targeted security tests written in the CATS YAML format

- `cats lint` will run OpenAPI contract linters, also called `Linter` Fuzzers

- `cats info` will print debug information that are useful when submitting bug reports

:::tip
Each sub-command has its own help. You can run `cats sub-command -h` to check all available arguments: `cats fuzz -h`.
:::