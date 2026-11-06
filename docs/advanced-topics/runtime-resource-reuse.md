---
sidebar_position: 8
description: Reuse identifiers learned from successful responses in later requests
---

# Runtime Resource Reuse

Starting with CATS 14.1.0, CATS keeps a run-local pool of resources observed in
successful API responses and uses their identifiers and baselines to make later
requests more likely to address real resources. This behavior is enabled by
default (`--reuseSuccessfulResources`) and does not require manual configuration.

## How CATS learns and reuses values

CATS learns resources and baselines from `2XX` responses returned by:

- `POST`, `PUT`, and `PATCH` requests (including `2XX` JSON response bodies, nested objects/arrays, wrapper envelopes, and primitive identifier arrays);
- collection and item `GET` requests, including arrays nested in a response object; and
- the response `Location` and `ETag` headers.

CATS reuses matching identifiers in path parameters, query parameters, request
headers, and identifier-like request-body fields (for both JSON and
`application/x-www-form-urlencoded` requests). It matches common names such as
`id`, `uuid`, `guid`, `token`, `petId`, `pet_id`, qualified nested identifiers,
and semantically similar resource names, validates candidate pool values against
the target field's OpenAPI schema `pattern`, `minLength`/`maxLength`, and
`format` constraints before injecting them, and cross-binds hierarchical path
parameters between leaf and parent paths. It also records successful request
baselines (`2XX`) so later mutations start from a proven valid request shape.
A successful `DELETE` (or a transition to an inactive/terminal lifecycle status
such as `CANCELLED`, `CLOSED`, `ARCHIVED`, or `DELETED`) invalidates the
matching resource identifier so that it is not reused later, and CATS can
automatically replay a recorded `POST` producer on demand when a downstream step
needs a fresh identifier.

Explicit user data always takes precedence. Values supplied through `--refData`,
`--urlParams`, or `--queryParams` are not overwritten, and CATS preserves the
intent of fields deliberately changed by a fuzzer. Runtime reuse only lasts for
the current run; the pool is cleared before a new run starts.

## Producer-first ordering and breadth-first execution waves

When `--reuseSuccessfulResources` is enabled and no `--pathsRunOrder` file is
supplied, CATS automatically orders root collection `POST` producer paths before
paths whose parameters or request bodies reference those identifiers. You can
inspect this computed execution order with:

```shell
cats list --paths -c contract.yml --executionOrder
```

To maximize coverage and resource availability across the run, CATS executes
fuzzers in breadth-first waves across all selected paths:

1. **Optional collection `GET` preseeding**: When `--preseedCollectionGets` is enabled, CATS calls up to `--maxPreseedCollectionGets` (default `20`) unparameterized collection `GET` operations without required query parameters before normal fuzzing to capture existing resource IDs.
2. **Seed wave**: Runs `HappyPath` and `OnlyRequiredFields` across all paths (`POST` and `GET` before `PUT`, `PATCH`, and `HEAD`) so runtime resources and baselines are populated before negative mutations run. `HappyPath` `DELETE` requests are postponed until the end of the run so created resources stay available.
3. **Coverage wave**: Runs the fuzzers from the `coverage` profile across all paths. If new resources are discovered during the coverage wave, CATS automatically retries `HappyPath` on safe seed operations that had not yet succeeded.
4. **Crash wave**: Runs the fuzzers from the `crash` profile across all paths.
5. **Remaining first-phase and path-level second-phase fuzzers**: Runs the remaining first-phase fuzzers and second-phase stateful HTTP checks (`CheckReadsStillWork`, `CreatedResourceLocation`, `UpdateReadConsistency`, `PatchOmittedFieldsClobber`, `ConditionalEtag`, `PutIdempotency`, `CrossParentResourceIsolation`, and `CheckDeletedResourcesNotAvailable`).
6. **Deferred `HappyPath` `DELETE` requests**: Runs postponed `HappyPath` `DELETE` requests in reverse path order (child resources before parent resources), followed by `CheckDeletedResourcesNotAvailable`.
7. **Time-bounded random continuation**: When `--stopAfterTimeInSec` is combined with `--fillTimeBudget` (`--withRandom`) or `-f RandomFuzzer` and time remains after all deterministic waves finish, CATS continues with `RandomFuzzer` rounds until the time budget expires.

## Diagnostics and reporting

When CATS applies a learned value, the individual test report records the source
request and response location, the target request field, and the reused value.
The summary report also shows whether runtime resource reuse was enabled.

When running with `-D` / `--debug`, CATS prints an end-of-run summary of any
documented operations that never succeeded with unmodified requests because of
unresolved identifier parameters, along with a suggested `--refData` YAML
snippet.

To disable runtime resource reuse for a run, use:

```shell
cats -c contract.yml -s http://localhost:8080 --no-reuseSuccessfulResources
```

The `FunctionalFlowFuzzer` supports explicit step-to-step correlation through its
[`save` variables and `$steps` references](../fuzzers/special-fuzzers/functional-fuzzer#saving-variables-save-and-cross-step-references-steps-prev).
Use those when a functional flow needs to pass specific response values to a
later request.
