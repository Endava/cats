---
sidebar_position: 20
description: Control CATS exit status in CI with quality gates
---

# Quality Gates and Stop Conditions

Use quality gates to decide when a CATS run should return exit code `1`, which
makes the result usable in CI/CD pipelines.

## Fail on result types

By default, CATS fails when at least one error is reported. Use `--failOn` to
include warnings:

```bash
cats -c openapi.yml -s http://localhost:8080 --failOn error,warn
```

Valid values are `error` and `warn`. The option controls build status; it does
not change how individual tests are reported.

## Numeric thresholds

Use `--qualityGate` when a run may contain a known number of findings:

```bash
cats -c openapi.yml -s http://localhost:8080 \
  --qualityGate "errors<5,warns<20"
```

Supported metrics are `errors` and `warns` (the alias `warnings` is also
accepted). A condition such as `errors<5` passes only while the error count is
below `5`; `errors>5` passes only when it is above `5`. Quality-gate rules take
precedence over `--failOn` when both are supplied.

The configured gate and its pass/fail status are included in the execution
summary and report.

## Baseline regression gating (`--baseline` and `cats compare`)

When onboarding an existing API that already has known findings, use `--baseline`
to compare the current run apples-to-apples against a previous `cats-report`
folder or `cats-summary-report.json` file:

```bash
cats -c openapi.yml -s http://localhost:8080 \
  --profile ci \
  --baseline ./baseline-cats-report
```

CATS compares persisted problem occurrences by problem ID and operation. For
accepted requests, CATS also tracks each mutated field as a separate occurrence.
Compatible server-message patterns can match when the two runs generalized
messages differently. Test-count changes are informational.

You can also compare existing report directories or `cats-summary-report.json`
files offline using `cats compare` (alias `cats diff`):

```bash
cats compare -b ./baseline-cats-report -t ./cats-report
cats compare -b ./baseline-cats-report -t ./cats-report --failOnNewWarnings -j
```

| State | Meaning |
| --- | --- |
| `NEW` | Observed only in the target report with comparable fuzzer coverage. |
| `FIXED` | Observed only in the baseline report with comparable fuzzer coverage. |
| `UNCHANGED` | Matched by identity or compatible server-message pattern. |
| `NOT_COMPARABLE` | None of the occurrence's fuzzers appear in the other report's saved tests. |

Keep the seed, configuration, and starting data consistent. `NEW` can include
a finding newly observed on an unchanged API build. Check saved coverage and
verify the finding before treating `FIXED` as a confirmed fix.

`cats compare` exits with `0` when there is no regression, `1` for new errors
(or new warnings with `--failOnNewWarnings`), and `2` for invalid input.
Both reports must contain persisted problems with `problemKeyVersion: 1`.
A run using `--baseline` also exits with `2` if CATS cannot load the baseline.
See the [16.0.0 migration guide](../upgrading-to-16.md) for JSON and CLI changes.

## Stop conditions

Stop a finite or continuous run after a limit is reached:

```bash
cats -c openapi.yml -s http://localhost:8080 --stopAfterTests 100
cats -c openapi.yml -s http://localhost:8080 --stopAfterErrors 5
cats random -c openapi.yml -s http://localhost:8080 \
  -p /users -X POST --mc 500 --stopAfterTimeInSec 60
```

- `--stopAfterTests` stops after the specified number of test cases. `--stopAfterMutations` is an alias.
- `--stopAfterErrors` stops after the specified number of error results.
- `--stopAfterTimeInSec` stops after the specified elapsed time.
- `--fillTimeBudget` (or `--withRandom`) continues fuzzing with `RandomFuzzer` after the deterministic fuzzers finish until the `--stopAfterTimeInSec` time budget expires.

The report records the reached stop condition and retains the results already
executed. These options apply to normal OpenAPI fuzzing, `cats random`, and
template fuzzing. When `--fillTimeBudget` (`--withRandom`) is combined with
`--stopAfterTimeInSec` during normal OpenAPI fuzzing, if the deterministic
fuzzers finish before the time budget expires, CATS continues running
`RandomFuzzer` rounds until the time limit is reached.
