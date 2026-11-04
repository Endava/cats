---
hide_table_of_contents: true
---

# Underflow Map Size Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `UnderflowMapSizeFieldsFuzzer` |
| **Log Key** | **UMSFF** |
| **Description** | Sends JSON objects/maps with fewer properties than the OpenAPI `minProperties` constraint (`minProperties - 1`) to verify minimum property count validation. |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | Object/map fields declaring `minProperties > 0` |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Replaces each object/map field that declares `minProperties > 0` with an object retaining only `minProperties - 1` properties (`{}` when `minProperties == 1`). |
| **Conditions when this fuzzer will be skipped** | When a field is not a JSON object/map or does not declare `minProperties > 0`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` if the server accepts the underflow object with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
