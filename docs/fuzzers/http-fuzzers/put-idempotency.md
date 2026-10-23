---
hide_table_of_contents: true
---

# PUT Idempotency

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `PutIdempotencyFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Second-phase fuzzer that repeats a previously successful `PUT` twice on a resource created in the current run and compares the resulting `GET` representations (ignoring volatile metadata fields such as `updatedAt`, `version`, `revision`, and `etag`). |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `2XX` with identical stable resource state across repeated identical `PUT` calls. |
| **Fuzzing logic** | Selects the latest successful `PUT` targeting a resource created during the current run, replays the `PUT` followed by a `GET`, then replays the identical `PUT` and `GET` again, and compares the two normalized JSON object snapshots. |
| **Conditions when this fuzzer will be skipped** | When the path has no in-scope `GET` without conditional headers, or when no successful `PUT` on a resource created in the current run was observed. |
| **HTTP methods that will be skipped** | `GET`, `POST`, `PATCH`, `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports `info` when the repeated `PUT` succeeds and the resource state remains unchanged, `warn` (`PUT retry rejected` or `PUT is not idempotent`) when a repeated `PUT` is rejected or changes stable resource fields, and `error` on `5XX`. |
