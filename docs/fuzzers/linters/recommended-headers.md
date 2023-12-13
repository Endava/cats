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
| **Reporting**                                                       | Reports `error` if the http method from path does not have the recommended headers, or `success` otherwise.                                          | 
