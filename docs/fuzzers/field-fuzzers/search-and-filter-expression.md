---
hide_table_of_contents: true
---

# Search And Filter Expression Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `SearchAndFilterExpressionFieldsFuzzer` |
| **Log Key** | **SAFEFF** |
| **Description** | Fuzzes search, query, and filter expression fields (`search`, `q`, `query`, `filter`, `keyword`, `keywords`, `pattern`, `regex`, `match`, `term`, `where`, `like`, `contains`) with malformed regex (`[unclosed(`), unbalanced query-parser syntax (`AND OR NOT [0 TO *}`), and trailing SQL `LIKE` escape sequences (`%\_%\`) to detect unhandled parser exceptions across backend runtimes and databases. |
| **Enabled by default?** | Yes (included in `security`, `ci`, `crash`, and `full` profiles) |
| **Target field types** | String and string-array fields/parameters matching search or filter naming |
| **Expected result when fuzzed field is required** | `4XX` or `2XX` |
| **Expected result when fuzzed field is optional** | `4XX` or `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` or `2XX` |
| **Fuzzing logic** | Replaces search and filter field values with malformed regex, Lucene/RSQL, and SQL `LIKE` wildcard escape payloads to verify that parser errors are caught and converted into `4XX` responses or safe literal searches rather than `500` crashes. |
| **Conditions when this fuzzer will be skipped** | When a field is not a string or string-array search/query/filter parameter. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` on `5XX` server errors or leaked parser exceptions; reports `success` when rejected with `4XX` or safely handled with `2XX`. |
