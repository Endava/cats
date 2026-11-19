---
hide_table_of_contents: true
---

# Numeric Parser Boundaries

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `NumericParserBoundaryFieldsFuzzer` |
| **Description** | Checks how numeric fields in JSON request bodies handle precision, exponent limits, and raw or quoted numeric representations. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | A controlled HTTP response without a server failure. |
| **Reporting** | Controlled `2XX`, `3XX`, and `4XX` responses are informational. Server and transport failures go through normal reporting rules. |
