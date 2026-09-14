---
sidebar_position: 92
description: Detect duplicate values in unique arrays
---

# DuplicateItemsInUniqueArraysFields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `DuplicateItemsInUniqueArraysFieldsFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Inserts a duplicate item into arrays declared with `uniqueItems: true`. |
| **Enabled by default?** | Yes. |
| **Target field types** | OpenAPI arrays with `uniqueItems: true` and a mutable non-empty value. |
| **Expected result** | `4XX`, because the duplicate violates the contract. |
| **Fuzzing logic** | Replaces each eligible array with a copy containing a duplicate item and sends the mutated request. |
| **Conditions when this fuzzer will be skipped** | When the payload is invalid JSON, no eligible unique array is present, the array is empty, or the target is reference data. |
| **HTTP methods that will be skipped** | `HEAD`, `GET`, `DELETE`. |
| **Reporting** | Reports a rejected duplicate as the expected result; unexpected response codes and server errors are reported as errors. |

This fuzzer targets arrays declared with `uniqueItems: true`. It replaces the
array with a value containing a duplicate item and checks whether the service
rejects the invalid request instead of silently accepting duplicate values.

Select it with:

```bash
cats -c openapi.yml -s http://localhost:8080 \
  --fuzzers=DuplicateItemsInUniqueArraysFields
```
