--- 
hide_table_of_contents: true
---

# Large Number of Random Headers

| Item                                               | Description                                                                                                                                                       |
|:---------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                               | LargeNumberOfRandomHeadersFuzzer                                                                                                                                  |
| **Log Key**                                        | **LNORH**                                                                                                                                                         |
| **Description**                                    | This adds 10 000 random headers to the request. Header's values can are random unicode values. The expectation is that APIs ignore them and process a happy path. |
| **Enabled by default?**                            | Yes                                                                                                                                                               |
| **Target header types**                            | N/A                                                                                                                                                               |
| **Expected result when fuzzed header is required** | `2XX` or `4XX`                                                                                                                                                    |
| **Expected result when fuzzed header is optional** | `2XX` or `4XX`                                                                                                                                                    |
| **Fuzzing logic**                                  | Add 10 000 random headers to every request path and http method.                                                                                                  |
| **Conditions when this fuzzer will be skipped**    | None                                                                                                                                                              |
| **HTTP methods that will be skipped**              | None                                                                                                                                                              |
| **Reporting**                                      | Reports `error` if: *1.* response code is other than `4XX` or `2XX`; <br/><br/> Reports `success` if: *1.* response code is `4XX` or `2XX`.                       | 
