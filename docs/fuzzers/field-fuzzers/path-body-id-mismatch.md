---
hide_table_of_contents: true
---

# Path Body ID Mismatch

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `PathBodyIdMismatchFuzzer` |
| **Log Key** | **PBIMF** |
| **Description** | Fuzzes write requests (`POST`, `PUT`, `PATCH`) where a URL path parameter (such as `{id}` or `{orderId}`) also appears as a top-level identifier field in the JSON request body. Sends a contradicting identifier in the request body while keeping the URL path parameter intact to detect split-brain updates, BOLA misrouting, and unhandled ORM primary-key mutation crashes. |
| **Enabled by default?** | Yes (included in `security`, `compliance`, `ci`, `crash`, and `full` profiles) |
| **Target field types** | Top-level request body identifier fields matching a URL `{path}` parameter (or `id` when the path parameter ends with `Id`) |
| **Expected result when fuzzed field is required** | `4XX` (or `2XX` if the server ignores the body ID and uses the URL path parameter) |
| **Expected result when fuzzed field is optional** | `4XX` (or `2XX` if the server ignores the body ID and uses the URL path parameter) |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Replaces the matching top-level body ID with a type-appropriate canary (`11111111-1111-1111-1111-111111111111` for UUIDs, `987654` for numbers, or `cats-mismatched-body-id` for strings) while keeping the URL path parameter unchanged. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, the URL has no path parameters, or the request body has no matching top-level ID field. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` (`Path and body ID mismatch accepted`) if a `2XX` response reflects the contradicting body canary ID, or `Server error` on `5XX`; reports `info` when rejected with `4XX` or ignored in favor of the URL path ID. |
