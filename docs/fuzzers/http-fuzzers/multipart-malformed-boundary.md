---
hide_table_of_contents: true
---

# Multipart Malformed Boundary

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `MultipartMalformedBoundaryFuzzer` |
| **Log Key** | **MMBF** |
| **Description** | Sends `multipart/form-data` requests with a mismatched, unterminated or malformed boundary. The expectation is that APIs reject the request as invalid. |
| **Enabled by default?** | Yes (included in `quick`, `ci`, `crash`, and `full` profiles) |
| **Expected result** | `4XX` |
| **Fuzzing logic** | Encodes the request as usual and sends three variants: *1.* the boundary used in the body differs from the one declared in the `Content-Type` header; *2.* the body has no closing boundary delimiter; *3.* the first boundary delimiter is not followed by a CRLF. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, the request body is not a `multipart/form-data` payload, or the path and HTTP method were already fuzzed (for example, for `oneOf`/`anyOf` payloads). |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE`, `OPTIONS` and any other method without a request body (the fuzzer runs on `POST`, `PUT`, `PATCH`, `PROPPATCH` and `REPORT`) |
| **Reporting** | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. |
