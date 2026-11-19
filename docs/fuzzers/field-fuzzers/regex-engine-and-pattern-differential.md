---
hide_table_of_contents: true
---

# Regex Engine and Pattern Differentials

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `RegexEngineAndPatternDifferentialFieldsFuzzer` |
| **Description** | Checks handling of regex-consuming string fields and strings with a contract pattern. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | `2XX` for accepted values or `4XX` for rejected values. |
| **Reporting** | Uses ordinary field-fuzzer response checks. |
