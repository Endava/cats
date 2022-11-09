---
sidebar_position: 2
description: Fuzz  with a custom dictionary and automatic configuration
---

# User Dictionary
This Fuzzer is a less configurable form of `SecurityFuzzer`. It is enabled when supplying a custom dictionary via de `-w` argument through the typical `cats ...` command.
Example:

`cats --contract=api.yml --server=https://cool-api.com -w nasty_list.txt --mc 500`

When supplying a custom dictionary you must also supply match arguments `--mX`. See the full list here: [all arguments](/docs/commands-and-arguments/arguments)

:::caution
All other Fuzzers will be disabled when supplying a custom dictionary using `-w`
:::

There are 2 Fuzzers available: `UserDictionaryHeadersFuzzer` and `UserDictionaryFieldsFuzzer`. 
Each of these Fuzzers will iterate through all request fields and headers and replace them with values from the custom dictionary.
The Fuzzer will report any response matching any of the `--mX` arguments as errors. All other responses will be skipped, therefore not added to the final report.

:::caution
At least one `--mX` argument is required when using a custom dictionary. The User Dictionary Fuzzers won't run without one.
:::

