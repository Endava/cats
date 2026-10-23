---
sidebar_position: 95
description: Replace JSON arrays with a single-element array containing null
---

# NullElementsInArrayFields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `NullElementsInArrayFieldsFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Replaces JSON array fields with a single-element array containing `null` (`[null]`) to verify element-level null handling when iterating over collections. |
| **Enabled by default?** | Yes; included in the `crash` and `full` profiles. |
| **Target field types** | JSON array fields whose `items` schema is not marked `nullable`. |
| **Expected result** | `4XX`, because an array element is `null`. |
| **Fuzzing logic** | Iterates through each non-nullable-item array field in the payload and replaces its value with `[null]`. |
| **Conditions when this fuzzer will be skipped** | When the payload has no array fields, the array's `items` schema allows `null`, or the field is supplied as reference data. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports a `4XX` rejection as the expected result; `5XX` server errors and unexpected response codes are reported as errors. |

This fuzzer targets collection-iteration bugs where an API validates that an
array property is non-null, but fails to check whether individual elements inside
the array are `null`.

```bash
cats -c openapi.yml -s http://localhost:8080 --fuzzers=NullElementsInArrayFields
```
