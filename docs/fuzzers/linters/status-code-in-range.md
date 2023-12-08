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
| **Reporting**                                                       | Reports `error` if at least one HTTP response code is invalid, or `success` otherwise.                                                   | 
