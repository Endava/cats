---
hide_table_of_contents: true
---

# PATCH Omitted Fields Clobber

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `PatchOmittedFieldsClobberFuzzer` |
| **Log Key** | **POFCF** |
| **Description** | Second-phase fuzzer that verifies RFC 5789 partial-update semantics on `PATCH` endpoints by reading an owned resource created in the current run, sending a partial `PATCH` payload that omits a previously populated optional field, and reading the resource back via `GET` to ensure the omitted field was not wiped to `null` or removed. |
| **Enabled by default?** | Yes (included in `security`, `ci`, and `full` profiles) |
| **Expected result** | `2XX` on `PATCH` and subsequent `GET`, with the omitted optional field preserving its pre-`PATCH` value |
| **Fuzzing logic** | Selects the latest successful `PATCH` on a resource created in the current run, captures a baseline `GET` snapshot, removes one non-volatile populated optional primitive field from the `PATCH` payload, sends the partial `PATCH`, and compares the subsequent `GET` snapshot. |
| **Conditions when this fuzzer will be skipped** | When the path has no in-scope item `GET`, when no successful `PATCH` on an owned resource was observed, or when no omittable populated optional field exists. |
| **HTTP methods that will be skipped** | `GET`, `POST`, `PUT`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `warn` (`PATCH wiped omitted optional fields`) if the omitted optional field becomes `null`, blank, or missing on the subsequent `GET`, `error` on `5XX`, and `info` when preserved. |
