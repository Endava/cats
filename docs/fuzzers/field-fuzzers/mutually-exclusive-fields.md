---
hide_table_of_contents: true
---

# Mutually Exclusive Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `MutuallyExclusiveFieldsFuzzer` |
| **Log Key** | **MEFF** |
| **Description** | Identifies sibling domain fields that represent mutually exclusive business inputs (`discountAmount`/`discountPercentage`, `creditAmount`/`debitAmount`, `includeIds`/`excludeIds`, `allowlist`/`blocklist`, or `endDate`/`durationDays`) and populates both simultaneously with active values to verify mutual-exclusion validation. |
| **Enabled by default?** | Yes (included in `ci` and `full` profiles) |
| **Target field types** | Sibling fields matching mutually exclusive domain groups |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Sets both fields in each discovered mutually exclusive pair to non-zero/non-empty active values in the same request, expecting `4XX`. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty or contains no mutually exclusive sibling field pairs. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` if the server accepts both mutually exclusive fields with `2XX` or fails with `5XX`; reports `success` when rejected with `4XX`. |
