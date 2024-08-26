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
| **Reporting**                                                       | Reports `success` if the operation has tracing headers, or `error` otherwise.                  | 
