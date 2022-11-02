# Empty Strings In Fields

| Item                                                                  | Value                                                                    |
|:----------------------------------------------------------------------|:-------------------------------------------------------------------------|
| **Enabled by default?**                                               | Yes                                                                      |
| **Target field types**                                                | All fields                                                               |
| **Expected result when required fields are fuzzed**                   | `4XX`                                                                    |
| **Expected result when optional fields are fuzzed**                   | `2XX`                                                                    |
| **Expected result when fuzzed values are not matching field pattern** | `4XX`                                                                    |
| **Fuzzing logic**                                                     | Iteratively **replaces** fields with empty values                        |
| **Conditions when this fuzzer will be skipped**                       | When HTTP method is `GET` or `DELETE` and field is NOT a query parameter |
| **HTTP methods that will be skipped**                                 | None                                                                     |