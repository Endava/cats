---
hide_table_of_contents: true
---

# Conditional ETag

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ConditionalEtagFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Second-phase fuzzer that uses previously observed `ETag` response headers to test `If-None-Match` on `GET` and a stale `If-Match` on `PUT` or `PATCH`. |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `304` for `GET` with `If-None-Match`; `412` (or `409`) for `PUT`/`PATCH` with a stale `If-Match`. |
| **Fuzzing logic** | On `GET`, replays a successful same-origin `GET` that returned an `ETag` with `If-None-Match` set to that tag. On `PUT`/`PATCH`, when two distinct `ETag` values were observed for a resource created in the current run, replays the update with the older (stale) `ETag` in `If-Match`. |
| **Conditions when this fuzzer will be skipped** | On `GET`, when no successful same-origin `GET` with an `ETag` was observed. On `PUT`/`PATCH`, when no two distinct `ETag` values were observed for a resource created in this run or the path lacks an eligible `GET`. |
| **HTTP methods that will be skipped** | `POST`, `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports `info` when `304` (for `GET`) or `412`/`409` (for stale `PUT`/`PATCH`) is returned, `warn` when a conditional `GET` returns `200` with the same `ETag` or a stale `If-Match` write succeeds (`2XX`), and `error` on `5XX`. |
