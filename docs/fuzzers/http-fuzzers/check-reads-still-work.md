---
hide_table_of_contents: true
---

# Check Reads Still Work

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `CheckReadsStillWorkFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Second-phase fuzzer that runs after all first-phase fuzzers finish for a path and repeats a `GET` request that succeeded before fuzzing, verifying that fuzzed `POST`, `PUT`, or `PATCH` requests did not store corrupt data that breaks subsequent reads. |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `2XX` (or `404`/`410` if a fuzzer deleted the resource). |
| **Fuzzing logic** | Repeats the unmodified `GET` request for a path that also defines write operations (`POST`, `PUT`, or `PATCH`) and had an unmodified `2XX` `GET` earlier in the run. |
| **Conditions when this fuzzer will be skipped** | When the path has no write operations (`POST`, `PUT`, or `PATCH`), or when `GET` did not succeed with an unmodified request earlier in the run. |
| **HTTP methods that will be skipped** | All except `GET` (`POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `TRACE`). |
| **Reporting** | Reports `error` (`Read broken after fuzzing`) on `5XX` responses, `info` on `404`/`410` (resource deleted during fuzzing), and normal `2XX` reporting otherwise. |
