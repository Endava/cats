--- 
hide_table_of_contents: true
---

# HTTP Status Code In Valid Range

| Item                                                                | Description                                                                                                                              |
|:--------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | HttpStatusCodeInRangeLinterFuzzer                                                                                                        |
| **Log Key**                                                         | **HSCIRL**                                                                                                                               |
| **Description**                                                     | This fuzzer will check if the OpenAPI specs have valid response codes (i.e. > 100 and < 599) defined for **all paths and HTTP methods**. |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                     |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `error` if at least one HTTP response code is invalid, or `success` otherwise.                                                   | 
