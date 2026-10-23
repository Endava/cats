---
hide_table_of_contents: true
---

# Update-Read Consistency

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `UpdateReadConsistencyFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Second-phase fuzzer that reads back a resource created and updated (`PUT` or `PATCH`) in the current run and compares present, writable primitive fields between the update request body and the subsequent `GET` response. |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `2XX` with persisted writable primitive fields matching the values sent in the update. |
| **Fuzzing logic** | Selects the latest successful `PUT` or `PATCH` on a resource created during the current run, sends a `GET` to the same resource path, and compares primitive fields that are present in both payloads and not marked `readOnly` or `writeOnly` (accounting for composed schemas, case-insensitive string trimming, numeric scale equality, and equivalent `OffsetDateTime` instants). |
| **Conditions when this fuzzer will be skipped** | When the path has no in-scope item `GET` or request schema, or when no successful `PUT`/`PATCH` on a resource created in the current run was observed. |
| **HTTP methods that will be skipped** | `GET`, `POST`, `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports `info` when comparable fields match (or when the resource was later deleted), `warn` (`Possible update/read inconsistency`) when sent and returned field values differ, and normal `2XX` expectation reporting on `5XX` or unexpected status codes. |
