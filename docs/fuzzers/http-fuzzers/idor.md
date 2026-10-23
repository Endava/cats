---
hide_table_of_contents: true
---

# Insecure Direct Object References

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `InsecureDirectObjectReferencesFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Detects heuristic IDOR signals by replacing ID-like request fields with alternative identifiers and verifying whether the alternative ID is returned in a successful response. |
| **Enabled by default?** | Yes. |
| **Target field types** | Numeric, UUID, and string fields whose names are ID-like, such as `id`, `userId`, `account_id`, or fields ending in `id`, `uuid`, or `guid`. |
| **Expected result** | `4XX`, indicating that the modified identifier was rejected or the resource was not available. |
| **Fuzzing logic** | Detects ID-like fields, generates alternative identifiers (such as `BigInteger` numeric offsets and UUIDs, while excluding known same-session identifiers from the runtime resource pool), replaces one field at a time, and inspects the response body. |
| **Conditions when this fuzzer will be skipped** | When no ID-like field is present, a field has no value, or the field is supplied as reference data. |
| **HTTP methods that will be skipped** | `HEAD`, `TRACE`. |
| **Reporting** | A `2XX` response whose JSON body contains the alternative identifier is reported as a `Heuristic IDOR signal detected` error; a `2XX` response that is empty/truncated or still contains only the original ID is reported as inconclusive (`info`); `4XX` is reported as correctly denied; `5XX` and other unexpected codes are errors. |
