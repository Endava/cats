---
sidebar_position: 96
description: Send complex query object parameters as JSON request bodies on non-body HTTP methods
---

# QueryObjectAsRequestBody

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `QueryObjectAsRequestBodyFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Sends complex query object parameters as JSON request bodies (required-only, full, and empty `{}`) on non-body HTTP methods such as `GET`. |
| **Enabled by default?** | Yes; included in the `coverage`, `crash`, and `full` profiles. |
| **Target field types** | Query parameters declared with a complex object schema on non-body HTTP operations. |
| **Expected result** | `2XX` or `4XX`. |
| **Fuzzing logic** | Extracts each complex object query parameter and sends its required-only JSON representation, full JSON representation, and `{}` as the HTTP request body. |
| **Conditions when this fuzzer will be skipped** | When the HTTP method requires a body (`POST`, `PUT`, `PATCH`), the payload is empty, or no query parameter has a complex object schema. |
| **HTTP methods that will be skipped** | `POST`, `PUT`, `PATCH`, `HEAD`, `TRACE`. |
| **Reporting** | Follows normal CATS reporting against the expected `2XX`/`4XX` response family; `5XX` server errors and unexpected response codes are reported as errors. |

Some frameworks and OpenAPI generators expose controller methods that combine a
non-body HTTP verb (such as `GET`) with a complex object parameter bound from the
request body. This fuzzer sends the complex query object as a JSON body to
exercise those handlers and detect server errors or unexpected behavior.

```bash
cats -c openapi.yml -s http://localhost:8080 --fuzzers=QueryObjectAsRequestBody
```
