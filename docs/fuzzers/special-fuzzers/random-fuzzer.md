---
sidebar_position: 5
description: Continuous fuzzing
---

# RandomFuzzer

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `RandomFuzzer` |
| **Log Key** | N/A; this command-level fuzzer uses the selected mutator in its scenarios. |
| **Description** | Continuously mutates generated requests using the registered mutators. |
| **Enabled by default?** | No. Enable it with `--random`. |
| **Target fields** | Request body fields, path/query parameters, and HTTP headers selected from the generated request. |
| **Expected result** | Controlled by the configured `--matchXXX`, `--ignoreXXX`, and `--filterXXX` arguments. |
| **Fuzzing logic** | Repeatedly selects a request target and a registered mutator, sends the mutated request, and continues until a stop condition is reached. |
| **Conditions when this fuzzer will be skipped** | When continuous fuzzing is not enabled or the configured stop condition has been reached. |
| **HTTP methods that will be skipped** | None beyond target-specific restrictions of the selected mutator. |
| **Reporting** | Uses the configured match, ignore, and filter rules; stop conditions can also terminate the run. |

This is the fuzzer behind the [Continuous Fuzzing Mode](/docs/getting-started/running-cats#continuous-fuzzing-mode). It will run based on the
registered mutators.
