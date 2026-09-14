--- 
hide_table_of_contents: true
---

# Tracing Headers

| Item                                                                | Description                                                                                                                                          |
|:--------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | TracingHeadersLinterFuzzer                                                                                                                           |
| **Log Key**                                                         | **THL**                                                                                                                                              |
| **Description**                                                     | This fuzzer will check that each path element and http methods defines recommended headers like: `Correlation-ID, Trace-ID, Session-ID, Request-ID`. |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                                 |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `error` if the http method from path does not have the recommended headers, or `success` otherwise.                                          | 
