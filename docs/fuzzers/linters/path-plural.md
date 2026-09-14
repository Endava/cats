--- 
hide_table_of_contents: true
---

# Path Plural

| Item                                                                | Description                                                                                      |
|:--------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | PathPluralsLinterFuzzer                                                                          |
| **Log Key**                                                         | **PPL**                                                                                          |
| **Description**                                                     | This linter will check if path uses plural nouns. For example, favor `/users` instead of `/user` |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                             |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `success` if the path uses plural nouns, or `error` otherwise.                           | 
