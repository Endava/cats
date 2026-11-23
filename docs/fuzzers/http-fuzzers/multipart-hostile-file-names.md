---
hide_table_of_contents: true
---

# Multipart Hostile File Names

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `MultipartHostileFileNamesFuzzer` |
| **Log Key** | **MHFNF** |
| **Description** | Sends `multipart/form-data` requests in which a file part has a hostile file name. The expectation is that APIs either sanitize the name and accept the upload, or reject it as invalid. |
| **Enabled by default?** | Yes (included in `security`, `ci`, and `full` profiles) |
| **Expected result** | `2XX` or `4XX` |
| **Fuzzing logic** | For each top-level property declared as `type: string` and `format: binary`, sends one request per hostile file name, keeping the file content: path traversal (`../../../../etc/passwd.pdf` and a Windows variant), absolute paths (`/etc/passwd.pdf` and `C:\Windows\win.ini.pdf`), a name with a NUL character, a name with a line break followed by an injected header, a name of 300 characters, `...`, a name made only of spaces, a name with a right-to-left override character, and a double extension (`evidence.pdf.exe`). The file name is written in the part header without escaping. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty, the request body is not a `multipart/form-data` payload, or the path and HTTP method were already fuzzed (for example, for `oneOf`/`anyOf` payloads). Also skipped for operations without top-level `type: string`, `format: binary` properties, as no cases are generated for them. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE`, `OPTIONS` and any other method without a request body (the fuzzer runs on `POST`, `PUT`, `PATCH`, `PROPPATCH` and `REPORT`) |
| **Reporting** | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. |
