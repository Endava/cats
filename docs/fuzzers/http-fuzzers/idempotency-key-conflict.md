---
hide_table_of_contents: true
---

# Idempotency Key Conflict

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `IdempotencyKeyConflictFuzzer` |
| **Log Key** | **IKCF** |
| **Description** | Verifies that endpoints declaring an idempotency header (such as `Idempotency-Key` or `X-Idempotency-Key`) reject a replayed idempotency key when sent with a modified request body, expecting `4XX` (such as `409 Conflict` or `422 Unprocessable Entity`). |
| **Enabled by default?** | Yes (included in `security`, `ci`, and `full` profiles) |
| **Expected result** | `2XX` on the initial request, followed by `4XX` when the same idempotency key is replayed with a mutated request payload |
| **Fuzzing logic** | Finds any header whose normalized name contains `idempotency`, sends an initial valid request with a fresh UUID key, and—if the initial request returns `2XX`—replays the exact same key with a modified request body field. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, no idempotency header is declared on the operation, or the initial baseline request does not return `2XX`. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE`, `PUT` |
| **Reporting** | Reports `error` if the conflicting replay is accepted with `2XX` or causes a `5XX` server error; reports `success` when rejected with `4XX`. |
