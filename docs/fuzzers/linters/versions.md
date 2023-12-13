--- 
hide_table_of_contents: true
---

# Versions

| Item                                                                | Description                                                                                                                                     |
|:--------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | VersionsLinterFuzzer                                                                                                                            |
| **Log Key**                                                         | **VL**                                                                                                                                          |
| **Description**                                                     | This fuzzer will check for versioning information in paths, servers definition or content type headers (in the form of `vXXX` or `versionXXX`). |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                            |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if no versioning information is found, or `success` otherwise.                                                                  | 
