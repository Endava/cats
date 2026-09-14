--- 
hide_table_of_contents: true
---

# Top Level Elements

| Item                                                                | Description                                                                                                                                                                                                                     |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | TopLevelElementsLinterFuzzer                                                                                                                                                                                                    |
| **Log Key**                                                         | **TLEL**                                                                                                                                                                                                                        |
| **Description**                                                     | This fuzzer will check that the OpenAPI spec contains the recommended top level elements: `"info.title", "info.description", "info.version", "info.contact.name", "info.contact.email", "info.contact.url", "servers", "tags"`. |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                                                                                                            |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `error` if the OpenAPI spec does not contain at least one of the recommended top elements, or `success` otherwise.                                                                                                      | 
