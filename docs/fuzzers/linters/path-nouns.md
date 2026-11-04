--- 
hide_table_of_contents: true
---

# Path Nouns

| Item                                                                | Description                                                                                                                         |
|:--------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | PathNounsLinter                                                                                                                     |
| **Log Key**                                                         | **PNL**                                                                                                                             |
| **Description**                                                     | Classifies path segments by REST role (namespace, collection, identifier, singleton, custom operation) and verifies that resource segments use nouns rather than verb-object or CRUD action names (for example `/users` instead of `/create-user`), while permitting valid terminal custom operations nested under a resource with a matching HTTP method (such as `POST /orders/{id}/cancel` or `GET /orders/search`). |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `success` if the path naming favours using nouns, or `error` otherwise.                                                     | 
