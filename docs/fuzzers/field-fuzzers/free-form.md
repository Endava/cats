---
sidebar_position: 91
description: Fuzz unconstrained OpenAPI arrays and objects
---

# Free-Form Fuzzers

OpenAPI schemas with unconstrained array items or unrestricted object
properties can hide unsafe parser and binding behavior. These fuzzers add
hostile values while preserving the rest of a generated request.

## FreeFormArrayItemsFields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `FreeFormArrayItemsFieldsFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Sends hostile values to arrays whose `items` schema is unconstrained. |
| **Enabled by default?** | Yes. |
| **Target field types** | OpenAPI arrays without an `items` schema. |
| **Expected result** | `2XX` or `4XX`; both are valid outcomes for a free-form array. |
| **Fuzzing logic** | Replaces each eligible array item with generated hostile values while preserving the surrounding request. |
| **Conditions when this fuzzer will be skipped** | When no eligible free-form array is present, the payload cannot be mutated, or the field contains reference data. |
| **HTTP methods that will be skipped** | `HEAD`, `GET`, `DELETE`. |
| **Reporting** | Reports the response against the expected `2XX`/`4XX` response family; unexpected response codes or server errors are reported as errors. |

## FreeFormObjectFields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `FreeFormObjectFieldsFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Adds hostile keys and values to objects with unrestricted `additionalProperties`. |
| **Enabled by default?** | Yes. |
| **Target field types** | OpenAPI objects with unrestricted `additionalProperties`. |
| **Expected result** | `2XX` or `4XX`; both are valid outcomes for a free-form object. |
| **Fuzzing logic** | Adds generated hostile properties to each eligible object while preserving the existing request data. |
| **Conditions when this fuzzer will be skipped** | When no eligible free-form object is present, the payload cannot be mutated, or the field contains reference data. |
| **HTTP methods that will be skipped** | `HEAD`, `GET`, `DELETE`. |
| **Reporting** | Reports the response against the expected `2XX`/`4XX` response family; unexpected response codes or server errors are reported as errors. |

- `FreeFormArrayItemsFields` targets arrays without an item schema and sends hostile values, expecting either a valid `2XX` response or a validation `4XX` response.
- `FreeFormObjectFields` targets objects with unrestricted `additionalProperties` and adds hostile keys and values, expecting either `2XX` or `4XX`.

Both are Field Fuzzers and can be selected with `--fuzzers=FreeForm` or
excluded with `--skipFuzzers=FreeForm`.
