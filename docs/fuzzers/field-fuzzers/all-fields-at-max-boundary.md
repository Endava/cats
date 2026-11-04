---
hide_table_of_contents: true
---

# All Fields At Max Boundary

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `AllFieldsAtMaxBoundaryFuzzer` |
| **Log Key** | **AFAMB** |
| **Description** | Sets all boundary-constrained fields in the request payload (`maxLength`, `maximum`, `exclusiveMaximum`) simultaneously to their maximum valid OpenAPI boundary in a single request to detect aggregate payload size, buffer, or database column overflow bugs. |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | String fields with `maxLength` and numeric/integer fields with `maximum` or `exclusiveMaximum` |
| **Expected result when fuzzed field is required** | `2XX` |
| **Expected result when fuzzed field is optional** | `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `2XX` |
| **Fuzzing logic** | Co-mutates every non-reference-data field that declares a valid upper bound (`maxLength` up to `10,000` characters, or `maximum` / `exclusiveMaximum - 1`) simultaneously in one request, preserving regex `pattern` constraints when present. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty or no fields declare `maxLength` or `maximum` / `exclusiveMaximum` bounds. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` if the server rejects the valid max-boundary request with `4XX` or `5XX`; reports `success` on `2XX` matching the documented response schema. |
