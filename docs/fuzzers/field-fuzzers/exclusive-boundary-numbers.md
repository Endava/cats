---
hide_table_of_contents: true
---

# Exclusive Boundary Numbers In Numeric Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ExclusiveBoundaryNumbersInNumericFieldsFuzzer` |
| **Log Key** | **EBNINFF** |
| **Description** | Sends the exact excluded boundary value on numeric fields declaring OpenAPI 3.0 `exclusiveMinimum: true` / `exclusiveMaximum: true` or OpenAPI 3.1 numeric `exclusiveMinimum` / `exclusiveMaximum` bounds, verifying that the endpoint enforces strict `<` and `>` comparisons rather than `<=` and `>=`. |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | `integer` and `number` fields with `exclusiveMinimum` or `exclusiveMaximum` |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Replaces each eligible numeric field with the exact excluded minimum and/or maximum boundary value. |
| **Conditions when this fuzzer will be skipped** | When a field is not numeric or does not declare `exclusiveMinimum` or `exclusiveMaximum`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` if the server accepts the excluded boundary with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
