# Decimal Numbers In Integer Fields

| Item                                                                  | Value                                                                |
|:----------------------------------------------------------------------|:---------------------------------------------------------------------|
| **Enabled by default?**                                               | Yes                                                                  |
| **Target field types**                                                | OpenAPI type `integer`                                               |
| **Expected result when required fields are fuzzed**                   | `4XX`                                                                |
| **Expected result when optional fields are fuzzed**                   | `4XX`                                                                |
| **Expected result when fuzzed values are not matching field pattern** | `4XX`                                                                |
| **Fuzzing logic**                                                     | Iteratively **replaces** `integer` fields with random decimal values |
| **Conditions when this fuzzer will be skipped**                       | When field is not of type `integer`                                  |
| **HTTP methods that will be skipped**                                 | None                                                                 |
