---
sidebar_position: 17
description: Mutators
---

# Mutators

Mutators are the fuzzers used by [continuous fuzzing](). You can list the built-in mutators using `cats list --mutators` sub-command.
Mutators are using more randomness than typical fuzzers. 
They either generate data on the fly or randomly select from a larger set. 
They also don't take into consideration data types, constraints, boundaries, etc.

## Custom Mutators

You can also define your own mutators using a simple syntax. A custom mutator is a `yaml` file with the following syntax:

```yaml
name: xss mutator
type: replace
values:
  - "<script>"
  - "alert(1)"
  - "console.log('hack')"
```

where:

- `name` is the mutator name
- `type` is one of `TRAIL, INSERT, PREFIX, REPLACE, REPLACE_BODY, IN_BODY`
- `values` an array of possible values that will be used by the mutator for random selection

Mutators should be grouped in a common folder. This is how you can supply a custom mutators location:

```shell
cats random --contract=openapi.yaml --server=http://localhost:8080 -H "API-KEY=$token" --mc 500 --path "/users/auth" -X POST -stopAfterTimeInSec 10 --mutators "./mutators"
```

`./mutators` must contain valid custom mutators files.

:::caution
Only one mutator is allowed per file.
:::