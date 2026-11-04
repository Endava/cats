---
hide_table_of_contents: true
---

# Non-Multiple-Of Numbers In Numeric Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `NonMultipleOfNumbersInNumericFieldsFuzzer` |
| **Log Key** | **NMONINFF** |
| **Description** | Sends numeric values that stay within `[minimum, maximum]` bounds but violate the OpenAPI `multipleOf` constraint (for example sending `15` when `multipleOf: 10`, or `0.025` when `multipleOf: 0.05`). |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | `integer` and `number` fields declaring `multipleOf` |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Computes a valid multiple within `[minimum, maximum]` and offsets it by `1` (for integers with `multipleOf > 1`) or `multipleOf / 2` (for decimals) so only the `multipleOf` constraint is violated. |
| **Conditions when this fuzzer will be skipped** | When a field is not numeric, does not declare `multipleOf`, or is an integer with `multipleOf <= 1`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` if the server accepts the non-multiple value with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
