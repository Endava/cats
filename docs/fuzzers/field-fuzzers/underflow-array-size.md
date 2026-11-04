---
hide_table_of_contents: true
---

# Underflow Array Size Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `UnderflowArraySizeFieldsFuzzer` |
| **Log Key** | **UASFF** |
| **Description** | Sends JSON arrays with fewer elements than the OpenAPI `minItems` constraint (`minItems - 1`) to verify minimum array size validation. |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | Array fields declaring `minItems > 0` |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Replaces each array field that declares `minItems > 0` with an array of size `minItems - 1` (`[]` when `minItems == 1`). |
| **Conditions when this fuzzer will be skipped** | When a field is not a JSON array or does not declare `minItems > 0`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` if the server accepts the underflow array with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
