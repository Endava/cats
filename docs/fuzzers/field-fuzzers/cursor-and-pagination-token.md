---
hide_table_of_contents: true
---

# Cursor And Pagination Token Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `CursorAndPaginationTokenFieldsFuzzer` |
| **Log Key** | **CAPTFF** |
| **Description** | Fuzzes cursor and continuation pagination token fields (`cursor`, `nextCursor`, `prevCursor`, `pageToken`, `nextPageToken`, `continuationToken`, `startingAfter`, `endingBefore`, `after`, `before`, `scrollId`, `searchAfter`) with Base64 payloads containing unexpected inner shapes (`{}` as `e30=`, `null` as `bnVsbA==`, non-numeric delimiter segments) and malformed Base64 padding (`===invalid-cursor===`). |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | String fields and query parameters matching cursor/pagination token names |
| **Expected result when fuzzed field is required** | `4XX` or `2XX` |
| **Expected result when fuzzed field is optional** | `4XX` or `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` or `2XX` |
| **Fuzzing logic** | Replaces cursor token fields with crafted Base64 and malformed token values to expose unhandled `IllegalArgumentException`, `NullPointerException`, or SQL/JSON cursor-decoding `500` errors. |
| **Conditions when this fuzzer will be skipped** | When a field is not a string cursor or pagination token field. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` on `5XX` server errors or unexpected status codes; reports `success` when rejected with `4XX` or safely handled with `2XX`. |
