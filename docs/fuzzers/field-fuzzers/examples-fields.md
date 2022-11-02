# Examples In Fields

| Item                                                                  | Value                                                                                         |
|:----------------------------------------------------------------------|:----------------------------------------------------------------------------------------------|
| **Enabled by default?**                                               | Yes                                                                                           |
| **Target field types**                                                | N/A                                                                                           |
| **Expected result when required fields are fuzzed**                   | `2XX`                                                                                         |
| **Expected result when optional fields are fuzzed**                   | `2XX`                                                                                         |
| **Expected result when fuzzed values are not matching field pattern** | `2XX`                                                                                         |
| **Fuzzing logic**                                                     | Iteratively sends raw object examples as defined in the OpenAPI specs.                        |
| **Conditions when this fuzzer will be skipped**                       | When the request does NOT have a defined `example` or `examples` object for the `requestBody` |
| **HTTP methods that will be skipped**                                 | None                                                                                          |