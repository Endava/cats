--- 
hide_table_of_contents: true
---

# CRLF

| Item                                               | Description                                                                                                               |
|:---------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                               | CRLFHeadersFuzzer                                                                                                         |
| **Log Key**                                        | **CRLFH**                                                                                                                 |
| **Description**                                    | This fuzzer replaces headers with CR & LF characters. The expectation is that APIs reject the request as invalid.         |
| **Enabled by default?**                            | Yes                                                                                                                       |
| **Target header types**                            | All                                                                                                                       |
| **Expected result when fuzzed header is required** | `4XX`                                                                                                                     |
| **Expected result when fuzzed header is optional** | `4XX`                                                                                                                     |
| **Fuzzing logic**                                  | Iteratively **replaces** headers with CR & LF characters.                                                                 |
| **Conditions when this fuzzer will be skipped**    | None                                                                                                                      |
| **HTTP methods that will be skipped**              | None                                                                                                                      |
| **Reporting**                                      | Reports `error` if: *1.* response code is other than `4XX`; <br/><br/> Reports `success` if: *1.* response code is `4XX`. | 
