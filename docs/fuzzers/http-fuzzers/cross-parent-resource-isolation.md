---
hide_table_of_contents: true
---

# Cross-Parent Resource Isolation

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `CrossParentResourceIsolationFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Second-phase fuzzer for hierarchical `GET` paths (containing both a parent and a child path parameter) that requests a previously observed child resource under a different observed parent identifier. |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `4XX` (`403` or `404`). |
| **Fuzzing logic** | Identifies nested item `GET` paths with at least two path parameters, pairs an observed child resource with a different parent identifier observed in the run (or from the runtime resource pool), and sends a `GET` request with the swapped parent parameter. |
| **Conditions when this fuzzer will be skipped** | When the `GET` path does not have both a parent and a final child path parameter, or when no alternative parent identifier was observed. |
| **HTTP methods that will be skipped** | `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports `info` on `403`/`404` or inconclusive responses, `warn` (`Potential cross-parent resource exposure`) when a `2XX` JSON response confirms the original child identifier under the swapped parent, and `error` on `5XX`. |
