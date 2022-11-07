--- 
hide_table_of_contents: true
---

# Unsupported Accept Headers

| Item                                               | Description                                                                                                                                                                                                                                                                                                                                                                                          |
|:---------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                               | UnsupportedAcceptHeadersFuzzer                                                                                                                                                                                                                                                                                                                                                                       |
| **Log Key**                                        | **UAHF**                                                                                                                                                                                                                                                                                                                                                                                             |
| **Description**                                    | This fuzzer will send different `Accept` headers from a pre-defined list. The Fuzzer will send happy path requests. The expectation is that APIs will reject requests as not supported.                                                                                                                                                                                                              |
| **Enabled by default?**                            | Yes                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Target header types**                            | All                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Expected result when fuzzed header is required** | N/A                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Expected result when fuzzed header is optional** | N/A                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Fuzzing logic**                                  | Iteratively **calls** all paths and HTTP methods and sends `Accept` headers from a pre-defined list of headers, which are not defined in the OpenAPI specs. Expects a `406` response code.                                                                                                                                                                                                           |
| **Conditions when this fuzzer will be skipped**    | None                                                                                                                                                                                                                                                                                                                                                                                                 |
| **HTTP methods that will be skipped**              | None                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Reporting**                                      | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not equal to `406`; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code `406` . | 

# List Of Accept Headers

```
"application/java-archive",
"application/javascript",
"application/octet-stream",
"application/ogg",
"application/pdf",
"application/xhtml+xml",
"application/x-shockwave-flash",
"application/ld+json",
"application/xml",
"application/zip",
"application/x-www-form-urlencoded",
"image/gif",
"image/jpeg",
"image/png",
"image/tiff",
"image/vnd.microsoft.icon",
"image/x-icon",
"image/vnd.djvu",
"image/svg+xml",
"multipart/mixed; boundary=cats",
"multipart/alternative; boundary=cats",
"multipart/related; boundary=cats",
"multipart/form-data; boundary=cats",
"text/css",
"text/csv",
"text/html",
"text/javascript",
"text/plain",
"text/xml"
```