--- 
hide_table_of_contents: true
---

# Only Multi Code Point Emojis

This Fuzzer has 2 flavours depending on the `--edgeSpacesStrategy`.

## Only Multi Code Point Emojis In Fields TRIM_AND_VALIDATE
| Item                                                                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzer                                                                                                                                                                                                                                                                                                                                                                                          |
| **Log Key**                                                         | **OMCPEIFF**                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **Description**                                                     | This fuzzer replaces fields with multi-codepoint emojis. The expectation is that APIs will sanitize the input values, thus removing emojis and handle the request as a happy path.                                                                                                                                                                                                                                                          |
| **Enabled by default?**                                             | No. You need to supply `--includeEmojis` argument                                                                                                                                                                                                                                                                                                                                                                                           |
| **Target field types**                                              | All                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Expected result when fuzzed field is required**                   | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed field is optional**                   | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed value is not matching field pattern** | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                                                   | Iteratively **replaces** fields with multi-codepoint emojis: 👩‍🚀                                                                                                                                                                                                                                                                                                                                                                          |
| **Conditions when this fuzzer will be skipped**                     | When field is a `discriminator`                                                                                                                                                                                                                                                                                                                                                                                                             |
| **HTTP methods that will be skipped**                               | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Reporting**                                                       | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 

## Only Control Characters In Fields VALIDATE_AND_TRIM
| Item                                                                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | OnlyMultiCodePointEmojisInFieldsValidateTrimFuzzer                                                                                                                                                                                                                                                                                                                                                                                          |
| **Log Key**                                                         | **OMCPEIFF**                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **Description**                                                     | This fuzzer replaces fields with multi-codepoint emojis. As the sanitization is assumed post-validation, the expectation is that APIs reject the request as invalid.                                                                                                                                                                                                                                                                        |
| **Enabled by default?**                                             | No. You need to supply `--includeEmojis` argument                                                                                                                                                                                                                                                                                                                                                                                           |
| **Target field types**                                              | All                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Expected result when fuzzed field is required**                   | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed field is optional**                   | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed value is not matching field pattern** | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                                                   | Iteratively **replaces** fields with multi-codepoint emojis: 👩‍🚀                                                                                                                                                                                                                                                                                                                                                                          |
| **Conditions when this fuzzer will be skipped**                     | When field is a `discriminator`                                                                                                                                                                                                                                                                                                                                                                                                             |
| **HTTP methods that will be skipped**                               | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Reporting**                                                       | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 