---
hide_table_of_contents: true
---

# Sort Column ORM Leak Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `SortColumnOrmLeakFieldsFuzzer` |
| **Log Key** | **SCOLFF** |
| **Description** | Fuzzes sort and order-by fields or query parameters (`sort`, `sortBy`, `sortColumn`, `sortField`, `orderBy`, `orderColumn`, `orderField`) with invalid or nested ORM property paths (`__cats_non_existent_column__`, `id.nonExistentNestedProperty`, `id ASC, __cats_invalid_column__`) to detect unhandled ORM `PropertyReferenceException` / `500` server errors. |
| **Enabled by default?** | Yes (included in `security`, `ci`, `crash`, and `full` profiles) |
| **Target field types** | String and string-array fields/parameters matching sort/order-by naming |
| **Expected result when fuzzed field is required** | `4XX` or `2XX` |
| **Expected result when fuzzed field is optional** | `4XX` or `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` or `2XX` |
| **Fuzzing logic** | Replaces sort/order-by field values with non-existent and nested ORM property expressions to verify that the API validates sort columns against an allowlist rather than passing raw strings to JPA/Hibernate/ORM sort builders. |
| **Conditions when this fuzzer will be skipped** | When a field is not a string or string-array sort/order-by parameter. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` on `5XX` server errors or leaked ORM stack traces; reports `success` when rejected with `4XX` or safely fallback-sorted with `2XX`. |
