--- 
hide_table_of_contents: true
---

# Minimum Exact Values In Numbers

| Item                                                                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | MinimumExactNumbersInNumericFieldsFuzzer                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Log Key**                                                         | **MENINFF**                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Description**                                                     | This fuzzer will iterate through `number` and `integer` fields and send values defined in the `minimum`(if present). The expectation is that APIs will treat the requests as happy paths. The Fuzzer aims to verify that all defined boundaries are properly implemented.                                                                                                                                                                   |
| **Enabled by default?**                                             | Yes                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Target field types**                                              | OpenAPI type `number` and `integer`                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Expected result when fuzzed field is required**                   | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed field is optional**                   | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed value is not matching field pattern** | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                                                   | Iteratively **replaces** `number` and `integer` fields that have a minimum defined with the exact minimum value.                                                                                                                                                                                                                                                                                                                            |
| **Conditions when this fuzzer will be skipped**                     | When field is not of type `number` or `integer` or the field is numeric but does not have a defined minimum                                                                                                                                                                                                                                                                                                                                 |
| **HTTP methods that will be skipped**                               | `GET` or `DELETE`                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Reporting**                                                       | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 