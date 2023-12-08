--- 
hide_table_of_contents: true
---

# Security Schemes

| Item                                                                | Description                                                                                                                 |
|:--------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | SecuritySchemesLinterFuzzer                                                                                                 |
| **Log Key**                                                         | **SSL**                                                                                                                     |
| **Description**                                                     | This fuzzer will check that each path element defines a `security` element or there is a global security element defined.   |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                        |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if the path does not have the `security` element nor is a global `securit` defined, or `success` otherwise. | 
