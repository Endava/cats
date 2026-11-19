---
hide_table_of_contents: true
---

# Unicode Normalization Differentials

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `UnicodeNormalizationDifferentialFieldsFuzzer` |
| **Description** | Checks whether string fields handle compatibility normalization and character conversion without a server failure. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | `2XX` for accepted values or `4XX` for rejected values. |
| **Reporting** | Uses ordinary field-fuzzer response checks. |
