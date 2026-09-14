--- 
hide_table_of_contents: true
---

# Query Params Case

| Item                                                                | Description                                                                          |
|:--------------------------------------------------------------------|:-------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | QueryParamsCaseLinterFuzzer                                                          |
| **Log Key**                                                         | **QPCL**                                                                             |
| **Description**                                                     | This linter will check if query params follow `snake_case` naming standard.          |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                 |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `success` if the query params use `snake_case` naming, or `error` otherwise. | 
