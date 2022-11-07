--- 
hide_table_of_contents: true
---

# Path Tags

| Item                                                                | Description                                                                           |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | PathTagsContractInfoFuzzer                                                            |
| **Log Key**                                                         | **PTCIF**                                                                             |
| **Description**                                                     | This fuzzer will check that each path element defines `tags`.                         |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                  |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if the path does not have the `tags` element, or `success` otherwise. | 
