---
hide_table_of_contents: true
---

# Created Resource Location

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `CreatedResourceLocationFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Second-phase fuzzer that retrieves a resource created by a successful `POST` (`201`) using its same-origin `Location` header when it points to an in-scope item `GET` operation. |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `2XX`. |
| **Fuzzing logic** | Inspects successful `POST` `201` exchanges recorded in the runtime resource pool, resolves same-origin `Location` headers that match a documented item `GET` path below the `POST` path, and sends a `GET` request to that URL. |
| **Conditions when this fuzzer will be skipped** | When `GET` is not in scope, or no `POST` `201` response with a same-origin `Location` matching a documented item `GET` was observed. |
| **HTTP methods that will be skipped** | `GET`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports `info` on `2XX`, `warn` (`Created resource unavailable`) on `404` or `410`, and normal `2XX` expectation reporting otherwise. |
