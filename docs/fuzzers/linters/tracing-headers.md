--- 
hide_table_of_contents: true
---

# Tracing Headers

| Item                                                                | Description                                                                                    |
|:--------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | TracingHeadersLinterFuzzer                                                                     |
| **Log Key**                                                         | **THL**                                                                                        |
| **Description**                                                     | This linter will check that each operation contains tracing headers like CorrelationId/TraceId |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                           |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `success` if the operation has tracing headers, or `error` otherwise.                  | 
