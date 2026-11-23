---
hide_table_of_contents: true
---

# Multipart Duplicate Parts

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `MultipartDuplicatePartsFuzzer` |
| **Log Key** | **MDPF** |
| **Description** | Sends `multipart/form-data` requests in which a part is sent twice, one part at a time. The expectation is that APIs reject the ambiguous request as invalid. |
| **Enabled by default?** | Yes (included in `security`, `crash`, and `full` profiles) |
| **Expected result** | `4XX` |
| **Fuzzing logic** | Encodes the request as usual and, for each top-level property of the payload, writes the part of that property twice in a row. Each duplicated part is sent as a separate test. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, the request body is not a `multipart/form-data` payload, or the path and HTTP method were already fuzzed (for example, for `oneOf`/`anyOf` payloads). |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE`, `OPTIONS` and any other method without a request body (the fuzzer runs on `POST`, `PUT`, `PATCH`, `PROPPATCH` and `REPORT`) |
| **Reporting** | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. |
