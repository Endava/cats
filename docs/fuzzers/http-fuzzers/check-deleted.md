--- 
hide_table_of_contents: true
---

# Check Deleted Resources Not Available

| Item                                            | Description                                                                                                                                                                                                                  |
|:------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                            | CheckDeletedResourcesNotAvailableFuzzer                                                                                                                                                                                      |
| **Log Key**                                     | **CDRNAF**                                                                                                                                                                                                                   |
| **Description**                                 | This fuzzer checks that deleted resources are not available anymore. The expectation is that a successful `DELETE` (`2XX`) will make a `GET` return a `404` or `410`. The Fuzzer will run last, after all the other Fuzzers. |
| **Enabled by default?**                         | Yes                                                                                                                                                                                                                          |
| **Expected result**                             | `404` or `410`.                                                                                                                                                                                                              |
| **Fuzzing logic**                               | Iteratively **sends** a `GET` for any successful `DELETE` recorded through the current run.                                                                                                                                  |
| **Conditions when this fuzzer will be skipped** | When no successful `DELETE` was recorded.                                                                                                                                                                                    |
| **HTTP methods that will be skipped**           | N/A                                                                                                                                                                                                                          |
| **Reporting**                                   | Reports `success` if response code is `404 or 410` and `error` otherwise.                                                                                                                                                    | 
