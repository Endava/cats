---
hide_table_of_contents: true
---

# Const And Not Schema Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ConstAndNotSchemaFieldsFuzzer` |
| **Log Key** | **CANSF** |
| **Description** | Violates OpenAPI 3.1 `const` constraints (by sending a different value of the same type) and OpenAPI 3.0/3.1 `not` schemas (by sending a value matching the forbidden `not` schema). |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | Fields declaring `const` or `not` schema constraints |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | For `const` fields, mutates the constant value (`number + 1`, `!boolean`, or `string + "_violatedConst"`). For `not` schemas, resolves the forbidden schema and synthesizes a value matching its `const`, `enum`, `default`, `example`, `minimum`, or type. |
| **Conditions when this fuzzer will be skipped** | When a field does not declare a `const` or `not` schema constraint. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` if the server accepts the violated constraint with `2XX` or crashes with `5XX`; reports `success` when rejected with `4XX`. |
