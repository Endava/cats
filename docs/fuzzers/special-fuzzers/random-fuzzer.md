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
| **Enabled by default?** | No. Enable it via `cats random`, `--random` (for `cats template`), or `--fillTimeBudget` / `--withRandom` (or `-f RandomFuzzer`) with `--stopAfterTimeInSec` during normal OpenAPI fuzzing. |
| **Target fields** | One or more request body fields, path/query parameters, and HTTP headers randomly selected per request. |
| **Expected result** | Controlled by the configured `--matchXXX`, `--ignoreXXX`, and `--filterXXX` arguments (or `5XX` server errors when running as a time-bounded continuation without explicit `--matchXXX` flags). |
| **Fuzzing logic** | Repeatedly selects one or more request fields and registered mutators, sends the mutated request, and continues until a stop condition is reached. When multiple operations are in scope during a time-bounded run, iterations are sliced per operation so a single endpoint does not consume the entire budget. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, no mutators are available, or the configured stop condition has been reached. |
| **HTTP methods that will be skipped** | None beyond target-specific restrictions of the selected mutator. |
| **Reporting** | Uses the configured match, ignore, and filter rules; in continuous time-bounded continuation without `--matchXXX`, HTTP `5XX` responses are reported as errors. |

This is the fuzzer behind the [Continuous Fuzzing Mode](/docs/getting-started/running-cats#continuous-fuzzing-mode). It runs based on the
registered [mutators](/docs/getting-started/mutators). Both `--path` (`-p`) and `--httpMethod` (`-X`) are optional when running `cats random`; if omitted, all paths and HTTP methods in the contract are fuzzed.
