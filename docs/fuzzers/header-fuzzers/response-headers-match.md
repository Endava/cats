--- 
hide_table_of_contents: true
---

# Response Headers Match Contract Headers

| Item                                            | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                            | ResponseHeadersMatchContractHeaders                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Log Key**                                     | **RHMCH**                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Description**                                 | This fuzzer will send a happy path request and match response headers against the ones defined in the contract. The expectation is that APIs will respond with a `2XX` and all declared contract headers.                                                                                                                                                                                                                                   |
| **Enabled by default?**                         | Yes                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Expected result**                             | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                               | Iteratively **sends** a happy path request for each path and HTTP method and checks that response headers match the ones defined in the contract.                                                                                                                                                                                                                                                                                           |
| **Conditions when this fuzzer will be skipped** | If contract doesn't declare any header or all declared headers are deprecated                                                                                                                                                                                                                                                                                                                                                               |
| **HTTP methods that will be skipped**           | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Reporting**                                   | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 