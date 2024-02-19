---
sidebar_position: 1
description: How a typical CATS run looks like
---

# Running CATS

## Blackbox Mode

Blackbox mode means that CATS doesn't need any specific context. You just need to provide the service URL, the OpenAPI spec and most probably [authentication headers](headers-file).

```bash
cats --contract=openapi.yaml --server=http://localhost:8080 --headers=headers.yml --blackbox
```

In blackbox mode CATS will only report `errors` if the received HTTP response code is a `5XX` (except `501`).
Any other mismatch between what the Fuzzer expects vs what the service returns (for example service returns `400` and CATS expects `200`) will be reported as `success`.

The blackbox mode is similar to a smoke test. It will quickly tell you if the application has major bugs that must be addressed **immediately**.

:::tip
`--blackbox` is actually equivalent to `--ignoreResponseCodes=2XX,4XX,501`. If you want your final report to only contain the `5XX` errors, you can use `--skipReportingForIgnored`.
:::

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
Continuous fuzzing has more randomness when executing: random selection of field to fuzz, fuzzing data is either generated on the fly or randomly picked
from a set of data. When doing continuous fuzzing you must also supply a `matchXXX` condition. This is used to flag the tests as `error` when that condition is met.

This is a typical continuous fuzzing run:

```shell
cats random --contract=openapi.yaml --server=http://localhost:8080 -H "API-KEY=$token" --mc 500 --path "/users/auth" -X POST --stopAfterTimeInSec 10
```

This will run continuous fuzzing for method `POST` on path `/users/auth`, flag any `500` response code as error and stop after 10 seconds.

As continuous fuzzing do not rely on pre-defined fuzzers, the `matchXXX` condition offers control on how each test case is flagged as error or not.
Continuous fuzzing is also more targeted, this is why supplying the `--path` and http method `-X` is required.

Continuous fuzzing is based on [mutators](./mutators.md).

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