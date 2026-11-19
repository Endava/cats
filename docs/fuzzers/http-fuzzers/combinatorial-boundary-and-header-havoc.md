---
hide_table_of_contents: true
---

# Combinatorial Boundary and Header Havoc

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `CombinatorialBoundaryAndHeaderHavocFuzzer` |
| **Description** | Checks robustness when request headers and multiple fields in a non-empty JSON request body vary together. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | A controlled HTTP response without a server failure. |
| **Reporting** | Controlled `2XX`, `3XX`, and `4XX` responses are informational. Server and transport failures go through normal reporting rules. |
