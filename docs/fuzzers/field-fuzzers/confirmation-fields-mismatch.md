---
hide_table_of_contents: true
---

# Confirmation Fields Mismatch

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ConfirmationFieldsMismatchFuzzer` |
| **Log Key** | **CFMF** |
| **Description** | Identifies sibling confirmation field pairs (such as `password`/`confirmPassword`, `email`/`confirmEmail`, or `iban`/`repeatIban`) and sends a mismatched value in the confirmation field to verify that the backend enforces cross-field equality. |
| **Enabled by default?** | Yes (included in `security`, `ci`, and `full` profiles) |
| **Target field types** | Sibling fields where one field prefixes or suffixes the other with `confirm`, `confirmation`, `repeat`, `verify`, `verification`, `retype`, or `match` |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Keeps the primary field intact and mutates the sibling confirmation field so the two values differ while preserving length/type shape. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty or contains no sibling confirmation field pairs. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` if the server accepts the mismatched pair with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
