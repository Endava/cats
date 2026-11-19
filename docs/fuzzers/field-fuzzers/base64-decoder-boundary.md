---
hide_table_of_contents: true
---

# Base64 Decoder Boundaries

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `Base64DecoderBoundaryFieldsFuzzer` |
| **Description** | Checks decoder robustness for strings declared as Base64 and cursor or pagination-token fields. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | `2XX` for accepted values or `4XX` for rejected values. |
| **Reporting** | Uses ordinary field-fuzzer response checks. |
