--- 
hide_table_of_contents: true
---

# Leading Whitespaces

| Item                                               | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:---------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                               | LeadingWhitespacesInHeadersFuzzer                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Log Key**                                        | **LWIH**                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Description**                                    | This fuzzer prefixes headers with [whitespaces](https://en.wikipedia.org/wiki/Whitespace_character). The expectation is that APIs will reject the requests as invalid.                                                                                                                                                                                                                                                                      |
| **Enabled by default?**                            | No. You need to supply `--includeWhitespaces` argument                                                                                                                                                                                                                                                                                                                                                                                      |
| **Target headers types**                           | All                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Expected result when fuzzed header is required** | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed header is optional** | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                                  | Iteratively **prefixes** headers with whitespaces. The Fuzzer contains 18 whitespaces characters like: `CR, LF, TAB, THIN SPACE`, etc.                                                                                                                                                                                                                                                                                                      |
| **Conditions when this fuzzer will be skipped**    | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **HTTP methods that will be skipped**              | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Reporting**                                      | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 