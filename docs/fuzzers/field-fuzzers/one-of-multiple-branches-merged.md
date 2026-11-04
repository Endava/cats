---
hide_table_of_contents: true
---

# OneOf Multiple Branches Merged Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `OneOfMultipleBranchesMergedFieldsFuzzer` |
| **Log Key** | **OOMBMFF** |
| **Description** | Merges properties from two distinct OpenAPI `oneOf` branches into a single JSON object to verify that the API enforces exclusive `oneOf` semantics (expecting `4XX`) rather than `anyOf`. |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | Request body objects (root or nested) composed with `oneOf` (`>= 2` branches) |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Takes a valid payload matching one `oneOf` branch and injects non-discriminator properties from a second `oneOf` branch into the same JSON object. |
| **Conditions when this fuzzer will be skipped** | When the payload is not a JSON object or contains no `oneOf` schema with distinct branch properties. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` if the server accepts the merged multi-branch payload with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
