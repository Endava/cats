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
| **Reporting**                                                       | Reports `error` if the OpenAPI spec does not contain at least one of the recommended top elements, or `success` otherwise.                                                                                                      | 
