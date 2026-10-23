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

CATS compares only the shared `(contractPath, httpMethod, fuzzer)` scopes
present in both runs and exits with code `1` only if **new** error regressions
are introduced (pass `--failOnNewWarnings` to also fail on new warnings).

You can also diff two existing report directories or `cats-summary-report.json`
files offline using `cats compare` (alias `cats diff`):

```bash
cats compare -b ./baseline-cats-report -t ./cats-report
cats compare -b ./baseline-cats-report -t ./cats-report --failOnNewWarnings -j
```

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
