# Default Values In Fields

| Item                                                                  | Value                                                                                         |
|:----------------------------------------------------------------------|:----------------------------------------------------------------------------------------------|
| **Enabled by default?**                                               | Yes                                                                                           |
| **Target field types**                                                | All fields                                                                                    |
| **Expected result when required fields are fuzzed**                   | `2XX`                                                                                         |
| **Expected result when optional fields are fuzzed**                   | `2XX`                                                                                         |
| **Expected result when fuzzed values are not matching field pattern** | `2XX`                                                                                         |
| **Fuzzing logic**                                                     | Iteratively **replaces** fields with their default values                                     |
| **Conditions when this fuzzer will be skipped**                       | When field does not have a default value OR field is an `enum` OR field is a `discriminator`  |
| **HTTP methods that will be skipped**                                 | None                                                                                          |