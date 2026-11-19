---
sidebar_position: 1
description: How a typical CATS run looks like
---

# Running CATS

## Pre-flight checks: `analyze`, `init`, and `smoke`

Before running the full fuzzing suite against an API for the first time, you can inspect the contract offline, scaffold starter configuration files (or install CATS AI agent skills), and run a 1-request-per-endpoint live probe:

```bash
# 1. Inspect the contract's 0-100 Fuzzability Readiness Score, producer-consumer chains, and orphan path params (offline)
cats analyze -c openapi.yaml

# 2. (Optional) Scaffold starter headers.yml, refData.yml, and functionalTests.yml
cats init -c openapi.yaml

# 3. (Optional) Install bundled CATS AI coding agent skills for Devin, Codex, Claude Code, OpenCode, and Cursor
cats init --skills
cats init --skills devin,claude,cursor --global

# 4. Probe one HappyPath request per operation (excluding DELETE by default) to verify --server and authentication
export API_AUTH='Bearer eyJ...'
cats smoke -c openapi.yaml -s http://localhost:8080 -H 'Authorization=$$API_AUTH'
```

After a fuzzing run finishes, use `cats triage` to review grouped problems, auto-mine `--refData` from `2XX` responses, synthesize `--correlations` rules, and verify fixes live against the server (`--verify`):

```bash
cats triage -r cats-report -c openapi.yaml \
  --export-refdata refData.yml \
  --export-correlations correlations.yml \
  --export-contract-patch contract-patch.yml \
  --export-reproducers reproducers.sh

# Verify fixes by replaying one representative request per problem occurrence
cats triage -r cats-report --verify -s http://localhost:8080
```

## Blackbox Mode

Blackbox mode (`--blackbox` or `-b`) focuses the report on `5XX` server
errors except `501` and high-severity security findings with explicit
`CONFIRMED` confidence. Use the OpenAPI contract, service URL, and any
[authentication headers](headers-file) the API requires.

```bash
cats -c openapi.yaml -s http://localhost:8080 --headers=headers.yml --blackbox
```

Other security candidates, including `FIRM`, `TENTATIVE`, unassessed and
keyword-only findings, become informational even with `--strict`. Use `-k` to
omit suppressed results and ordinary successes. Explicit status-code ignores
still apply, so `-b -i 2xx` also suppresses confirmed findings returned on `2XX`.
Exceptional-response and response-time diagnostics remain available. Review the
recorded evidence for retained findings.

For the CATS 15.x status filter, pass `-i 2xx,4xx,501` without `-b`. See
[Upgrading to CATS 16.0.0](../upgrading-to-16.md#blackbox-mode).

## Request pacing and rate limiting

`--maxRequestsPerMinute` sets the configured request ceiling. When the API
returns `429`, CATS can lower its effective request rate and gradually
recover toward the configured rate after consecutive non-`429` responses.
CATS also retries `503` responses that carry `Retry-After`. Unused request permits do not accumulate during idle periods.

`--maxRateLimitRetries` defaults to `3` and accepts `0` to disable retries.
`--maxRetryAfterInSec` caps the wait per retry and defaults to `30` seconds.
CATS skips the retry when the server asks for a wait above the cap, the wait
would exceed the run deadline, or the test explicitly expects `429`.
A `429` left unresolved by the retry policy is reported as a **Rate limited**
warning because the verdict is inconclusive. An unresolved `503` follows
ordinary response reporting.

## Context Mode

The second mode is called Context Mode.
Each Fuzzer has an expected HTTP response code based on the scenario under test and will also check if the response is matching the schema defined in the OpenAPI spec corresponding to that response code.
This will allow you to tweak either your OpenAPI spec or service behaviour in order to create good quality APIs and documentation and also to avoid possible serious bugs.

Running CATS in context mode usually implies providing a [--refData](reference-data-file) file with pre-existing resource identifiers or values that needs to be fixed (or from a limited set).
CATS cannot create data on its own (yet), so it's important that any request field or query param that requires pre-existence of those entities/resources to be created in advance and provided through the reference data file.

```bash
cats --contract=openapi.yaml --server=http://localhost:8080 --headers=headers.yml --refData=referenceData.yml
```

## Continuous Fuzzing Mode

The first two modes are deterministic. Running CATS multiple times with the same configuration will (most likely) produce the same output: same number of tests, same number of failing tests with the same reason.

CATS can also do continuous fuzzing i.e. fuzz until a certain stop condition is met, either time based, failing tests or number of tests. 
Continuous fuzzing has more randomness when executing: random selection of one or multiple fields to fuzz per request, and fuzzing data that is either generated on the fly or randomly picked
from a set of data. You can supply a `matchXXX` condition to flag tests as `error` when that condition is met.

This is a typical continuous fuzzing run:

```shell
cats random --contract=openapi.yaml --server=http://localhost:8080 -H "API-KEY=$token" --mc 500 --path "/users/auth" -X POST --stopAfterTimeInSec 10
```

This will run continuous fuzzing for method `POST` on path `/users/auth`, flag any `500` response code as error and stop after 10 seconds.

Both `--path` (`-p`) and `--httpMethod` (`-X`) are optional: if omitted, `cats random` fuzzes all paths and HTTP methods in the contract.

In addition, when running normal OpenAPI fuzzing with `--stopAfterTimeInSec` and `--fillTimeBudget` (or `--withRandom`), if all deterministic fuzzer waves finish before the time limit expires, CATS uses the remaining time to run continuous `RandomFuzzer` rounds across operations with non-empty payloads, reporting `5XX` responses as errors even when no `--matchXXX` flags are supplied.

:::caution
It's recommended to supply a `--stopXXX` argument in order for CATS to stop the execution when the supplied condition is met. 
Otherwise, it will run indefinitely and won't produce a summary report. It will still produce individual .json and .html test reports.
:::

Continuous fuzzing is based on [mutators](./mutators.md).

## Profiles and health checks

Use a [fuzzer profile](./fuzzer-profiles) to select a predefined test suite:

```shell
cats -c openapi.yaml -s http://localhost:8080 --profile quick
```

To verify reachability with the minimal `HappyPath` profile before starting a
full run, use `--healthCheck`:

```shell
cats -c openapi.yaml -s http://localhost:8080 --healthCheck
```

The `--mode NEGATIVE` and `--mode POSITIVE` options provide another way to
select negative or positive scenarios across the registered fuzzers.

For CI, use [quality gates and stop conditions](./quality-gates) to control
exit status and bound the run.

## Notes on Skipped Tests
:::info
You may notice a significant number of tests marked as `skipped`. CATS tries to apply all Fuzzers to all fields, but this is not always possible.
For example the `RandomStringInBooleanFieldsFuzzer` cannot be applied to `string` fields. This is why that test attempt will be marked as skipped.
It was an intentional decision to also report the `skipped` tests in order to show that CATS actually tries all the Fuzzers on all the fields/paths/endpoints.
Skipped tests are not included in the final report.
:::

## Notes on Console Output
:::caution
When running with `--verbosity DETAILED` CATS produces a significant amount of logging.
If the output is redirected to a file please make sure you do proper cleanup when not needing it.
A simple run can produce tens of MB of data. 
You have several options to control the logging level using: `--log, --skipLog, --onlyLog` arguments. 
For example `--log=error` will only log errors. Or you can keep the default `--verbosity SUMMARY` which has a very condensed output. 
:::

:::tip
Additionally, CATS support a lot [more arguments](/docs/commands-and-arguments/arguments) that allows you to restrict the number of fuzzers, provide timeouts, limit the number of requests per minute and so on.
:::
