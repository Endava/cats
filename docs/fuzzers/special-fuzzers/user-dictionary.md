---
sidebar_position: 2
description: Fuzz  with a custom dictionary and automatic configuration
---

# User Dictionary

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `UserDictionaryFieldsFuzzer` and `UserDictionaryHeadersFuzzer` |
| **Log Key** | N/A; the active fuzzer name is shown in test results. |
| **Description** | Replaces request fields or headers with values from a user-supplied dictionary. |
| **Enabled by default?** | No. Supplying `-w` or `--words` activates the user-dictionary fuzzers. |
| **Target fields/headers** | All eligible request fields and HTTP headers. |
| **Expected result** | Responses matching at least one configured `--matchXXX` argument are reported as errors; other responses are skipped. |
| **Fuzzing logic** | Iterates through dictionary values and replaces each eligible field or header. |
| **Conditions when this fuzzer will be skipped** | When no custom dictionary is supplied or no `--matchXXX` argument is configured. Other fuzzers are disabled while the custom dictionary is active. |
| **HTTP methods that will be skipped** | None beyond normal target-specific restrictions. |
| **Reporting** | Reports configured match conditions as errors and filters all other responses from the final report. |

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
