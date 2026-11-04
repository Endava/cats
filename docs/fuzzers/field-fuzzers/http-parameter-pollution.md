---
hide_table_of_contents: true
---

# HTTP Parameter Pollution Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `HttpParameterPollutionFieldsFuzzer` |
| **Log Key** | **HPPFF** |
| **Description** | Duplicates query parameters in the request URL with conflicting second values (for example `?status=ACTIVE&status=catsPolluted`) to detect HTTP Parameter Pollution (HPP) and array-coercion crashes. |
| **Enabled by default?** | Yes (included in `security`, `ci`, `crash`, and `full` profiles) |
| **Target field types** | URL query parameters |
| **Expected result when fuzzed field is required** | `4XX` or `2XX` |
| **Expected result when fuzzed field is optional** | `4XX` or `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` or `2XX` |
| **Fuzzing logic** | Iterates through declared query parameters and appends a duplicate query parameter with a conflicting value (`catsPolluted` for strings, `-999999` for numbers, or the opposite boolean). |
| **Conditions when this fuzzer will be skipped** | When the operation declares no query parameters. |
| **HTTP methods that will be skipped** | `HEAD`, `TRACE` |
| **Reporting** | Reports `error` on `5XX` server errors or unexpected status codes; reports `success` on `4XX` or `2XX` matching the contract. |
