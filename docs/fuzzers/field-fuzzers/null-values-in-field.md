--- 
hide_table_of_contents: true
---

# Null Values

| Item                                                                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | NullValuesInFieldsFuzzer                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Log Key**                                                         | **NVIF**                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Description**                                                     | This fuzzer will send null values in fields. The expectation is that APIs will reject the request as invalid for required fields.                                                                                                                                                                                                                                                                                                           |
| **Enabled by default?**                                             | Yes                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Target field types**                                              | All fields                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Expected result when fuzzed field is required**                   | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed field is optional**                   | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed value is not matching field pattern** | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                                                   | Iteratively **replaces** fields with null values                                                                                                                                                                                                                                                                                                                                                                                            |
| **Conditions when this fuzzer will be skipped**                     | When not a HTTP verb with body AND fuzzed field is a path parameter                                                                                                                                                                                                                                                                                                                                                                         |
| **HTTP methods that will be skipped**                               | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Reporting**                                                       | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 