---
hide_table_of_contents: true
---

# Insecure Direct Object References

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `InsecureDirectObjectReferencesFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Replaces ID-like request fields with alternative identifiers to detect unauthorized access to another user's or resource's data. |
| **Enabled by default?** | Yes. |
| **Target field types** | Numeric, UUID, and string fields whose names are ID-like, such as `id`, `userId`, `account_id`, or fields ending in `id`, `uuid`, or `guid`. |
| **Expected result** | `4XX`, indicating that the modified identifier was rejected or the resource was not available. |
| **Fuzzing logic** | Detects ID-like fields, generates alternative identifiers, replaces one field at a time, and checks whether the response exposes a successful or server-error path. |
| **Conditions when this fuzzer will be skipped** | When no ID-like field is present, a field has no value, or the field is supplied as reference data. |
| **HTTP methods that will be skipped** | None. |
| **Reporting** | A `2XX` response is reported as a potential IDOR error; `4XX` is reported as correctly denied, while `5XX` and other unexpected codes are errors. |
