# Abugidas in String Fields

This Fuzzer has 2 flavours depending on the `--sanitizationStrategy`.

## Abugidas in String Fields SanitizeAndValidate Fuzzer
| Item                                                                  | Value                                                                                                               |
|:----------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------|
| **Enabled by default?**                                               | Yes                                                                                                                 |
| **Target field types**                                                | OpenAPI type `string`                                                                                               |
| **Expected result when required fields are fuzzed**                   | `2XX`                                                                                                               |
| **Expected result when optional fields are fuzzed**                   | `2XX`                                                                                                               |
| **Expected result when fuzzed values are not matching field pattern** | `4XX`                                                                                                               |
| **Fuzzing logic**                                                     | Iteratively **inserts** abugigas characters in `string` fields                                                      |
| **Conditions when this fuzzer will be skipped**                       | When field is not of type `string` OR field is an `enum` OR field is a `discriminator` OR field is `reference data` |
| **HTTP methods that will be skipped**                                 | None                                                                                                                |


## Abugidas in String Fields ValidateAndSanitize Fuzzer
| Item                                                                  | Value                                                                                                               |
|:----------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------|
| **Enabled by default?**                                               | Yes                                                                                                                 |
| **Target field types**                                                | OpenAPI type `string`                                                                                               |
| **Expected result when required fields are fuzzed**                   | `4XX`                                                                                                               |
| **Expected result when optional fields are fuzzed**                   | `4XX`                                                                                                               |
| **Expected result when fuzzed values are not matching field pattern** | `4XX`                                                                                                               |
| **Fuzzing logic**                                                     | Iteratively **inserts** abugigas characters in `string` fields                                                      |
| **Conditions when this fuzzer will be skipped**                       | When field is not of type `string` OR field is an `enum` OR field is a `discriminator` OR field is `reference data` |
| **HTTP methods that will be skipped**                                 | None                                                                                                                |
