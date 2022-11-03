
# Fuzzers

To get a list of fuzzers and a short description run `cats list --fuzzers`.

There are multiple categories of Fuzzers available:

- `Field` Fuzzers which target request body fields or path parameters
- `Header` Fuzzers which target HTTP headers
- `HTTP` Fuzzers which target just the interaction with the service (without fuzzing fields or headers)

Additional checks which are not actually using any fuzzing, but leverage the CATS internal model for consistency and are also called Fuzzers:

- `ContractInfo` Fuzzers which checks the contract for API good practices
- `Special` Fuzzers a special category which need further configuration and are focused on more complex activities like functional flow, custom dictionaries or supplying your own request templates, rather than OpenAPI specs.

Each Fuzzer from the above categories are individually detailed using the below description table.

## Understanding the Description Table for Each Fuzzer

| Item                                                                | Description                                                                                                                                                                           |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | The full name of the Fuzzer as printed in the logs and in reports                                                                                                                     |
| **Log Key**                                                         | The key used to prefix log lines. This is useful to understand all processing happening through the lifecycle of the Fuzzer.                                                          |
| **Description**                                                     | A brief description on how this Fuzzer work and what is expected behavior from the API.                                                                                               |
| **Enabled by default?**                                             | Is this Fuzzer enabled by default or does it need additional arguments to be present.                                                                                                 |
| **Target field types**                                              | What is the target of this Fuzzer. Some Fuzzers might target only `string` fields, while others might only target `boolean` fields. Some Fuzzer might even target specific `format`s. |
| **Expected result when fuzzed field is required**                   | What is the expected HTTP response code when the fuzzed field is marked as `required`.                                                                                                |
| **Expected result when fuzzed field is optional**                   | What is the expected HTTP response code when the fuzzed field is optional (i.e. not explicitly marked as `required`).                                                                 |
| **Expected result when fuzzed value is not matching field pattern** | What is the expected HTTP response code when the fuzzed value does not match the field `pattern` (if defined).                                                                        |
| **Fuzzing logic**                                                   | The logic of the fuzzer. Is it replacing values? Prefixing? Suffixing?                                                                                                                |
| **Conditions when this fuzzer will be skipped**                     | What are the conditions when the Fuzzer will be skipped? Some examples: when the field is a `discriminator`, part of `reference data`, etc.                                           |
| **HTTP methods that will be skipped**                               | Some Fuzzers cannot apply to specific HTTP methods. This will list all methods that will be skipped for the Fuzzer.                                                                   |
| **Reporting**                                                       | What are the conditions when the Fuzzer will report `error`, `warn` or `success`.                                                                                                     | 


Clarifications on the wording from the `Description` column:
- `happy path` means that CATS expects a `2XX` HTTP response code
- `rejects request as invalid` means that CATS expects a `4XX` HTTP response code
- `response code is documented` means that that HTTP response code is documented as a potential response code in the OpenAPI specs
- `resopnse code is expected` means that CATS expects to receive this HTTP response code for the current Fuzzer (taking into consideration the above 2 `Expected result when...` conditions)
- `unexpected exception` means anything that happens outside the rest of the conditions (Connection Timeout for example)
- `not matches response schema` means that the structure of the HTTP response body does not match the schema defined in the OpenAPI specs
- `response code is expected, but not documented` means that the received HTTP response code matches Fuzzer's expectations, but the HTTP response code is not documented in the OpenAPI specs as a potential response code


```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```