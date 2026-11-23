---
hide_table_of_contents: true
---

# Multipart Oversized File

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `MultipartOversizedFileFuzzer` |
| **Log Key** | **MOFF** |
| **Description** | Sends `multipart/form-data` requests in which a file part is larger than the configured size. The expectation is that APIs enforce a maximum upload size and reject the request. |
| **Enabled by default?** | Yes (included in `security`, `compliance`, and `full` profiles) |
| **Expected result** | `4XX` |
| **Fuzzing logic** | For each top-level property declared as `type: string` and `format: binary`, sends the request with that file part set to exactly `MultipartOversizedFile.sizeBytes` bytes (default `10485761`, which is 10 MiB plus one byte): the original content is cut or padded with zero bytes. The size can be changed using the [fuzzers configuration file](/docs/advanced-topics/fuzzers-config). |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, the request body is not a `multipart/form-data` payload, or the path and HTTP method were already fuzzed (for example, for `oneOf`/`anyOf` payloads). Also skipped for operations without top-level `type: string`, `format: binary` properties, as no cases are generated for them. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE`, `OPTIONS` and any other method without a request body (the fuzzer runs on `POST`, `PUT`, `PATCH`, `PROPPATCH` and `REPORT`) |
| **Reporting** | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. |
