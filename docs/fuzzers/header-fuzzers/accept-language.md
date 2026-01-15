---
hide_table_of_contents: true
---

# Accept-Language

| Item                                               | Description                                                                                                                                                                                         |
|:---------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                               | AcceptLanguageHeadersFuzzer                                                                                                                                                                         |
| **Log Key**                                        | **ALH**                                                                                                                                                                                             |
| **Description**                                    | This fuzzer sends a happy-flow request while adding an `Accept-Language` header with different locale values. The expectation is that APIs accept the request and respond successfully.             |
| **Enabled by default?**                            | Yes                                                                                                                                                                                                 |
| **Target header types**                            | `Accept-Language` (locale)                                                                                                                                                                          |
| **Expected result when fuzzed header is required** | `2XX` (specifically `200`)                                                                                                                                                                          |
| **Expected result when fuzzed header is optional** | `2XX` (specifically `200`)                                                                                                                                                                          |
| **Fuzzing logic**                                  | Iteratively **adds** an `Accept-Language` header with values: `fr`, `zh-CN`, `ar-SA`, `en-GB`, `tlh-KL`. Each value is sent in an independent request, preserving the rest of the original headers. |
| **Conditions when this fuzzer will be skipped**    | None                                                                                                                                                                                                |
| **HTTP methods that will be skipped**              | None                                                                                                                                                                                                |
| **Reporting**                                      | Reports `error` if: *1.* response code is other than `200`; <br/><br/> Reports `success` if: *1.* response code is `200`.                                                                           |