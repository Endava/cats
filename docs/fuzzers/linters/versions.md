--- 
hide_table_of_contents: true
---

# Versions In Path

| Item                                                                | Description                                                                                                                                             |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | VersionsLinterFuzzer                                                                                                                                    |
| **Log Key**                                                         | **VL**                                                                                                                                                  |
| **Description**                                                     | This fuzzer will check that each path element does not contain version info (in the form of `vXXX`, but rather the version is defined in the `baseUrl`. |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                                    |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if the path contains version information, or `success` otherwise.                                                                       | 
