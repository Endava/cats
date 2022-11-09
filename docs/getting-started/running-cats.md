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

In blackbox mode CATS will only report `errors` if the received HTTP response code is a `5XX`.
Any other mismatch between what the Fuzzer expects vs what the service returns (for example service returns `400` and CATS expects `200`) will be reported as `success`.

The blackbox mode is similar to a smoke test. It will quickly tell you if the application has major bugs that must be addressed **immediately**.

:::tip
`--blackbox` is actually equivalent to `--ignoreResponseCodes=2XX,4XX`. If you want your final report to only contain the `5XX` errors, you can use `--skipReportingForIgnored`.
:::

## Context Mode

The real power of CATS relies on running it in a non-blackbox mode also called Context Mode.
Each Fuzzer has an expected HTTP response code based on the scenario under test and will also check if the response is matching the schema defined in the OpenAPI spec corresponding to that response code.
This will allow you to tweak either your OpenAPI spec or service behaviour in order to create good quality APIs and documentation and also to avoid possible serious bugs.

Running CATS in context mode usually implies providing a [--refData](reference-data-file) file with pre-existing resource identifiers or values that needs to be fixed (or from a limited set).
CATS cannot create data on its own (yet), so it's important that any request field or query param that requires pre-existence of those entities/resources to be created in advance and provided through the reference data file.

```bash
cats --contract=openapi.yaml --server=http://localhost:8080 --headers=headers.yml --refData=referenceData.yml
```

## Notes on Skipped Tests
:::info
You may notice a significant number of tests marked as `skipped`. CATS tries to apply all Fuzzers to all fields, but this is not always possible.
For example the `RandomStringInBooleanFieldsFuzzer` cannot be applied to `string` fields. This is why that test attempt will be marked as skipped.
It was an intentional decision to also report the `skipped` tests in order to show that CATS actually tries all the Fuzzers on all the fields/paths/endpoints.
Skipped tests are not included in the final report.
:::

## Notes on Console Output
:::caution
CATS produces a significant amount of logging.
If the output is redirected to a file please make sure you do proper cleanup when not needing it.
A simple run can produce tens of MB of data. You can control the logging level using the `--log` argument. For example   `--log=error` will only log errors.
:::

:::tip
Additionally, CATS support a lot [more arguments](/docs/commands-and-arguments/arguments) that allows you to restrict the number of fuzzers, provide timeouts, limit the number of requests per minute and so on.
:::