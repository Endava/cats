---
hide_table_of_contents: true
---

# JSON Nesting Depth

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `JsonNestingDepthFuzzer` |
| **Description** | Checks JSON parser behavior with bounded complete and incomplete nested bodies on JSON request operations. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | A controlled HTTP response without a server failure. |
| **Reporting** | Controlled `2XX`, `3XX`, and `4XX` responses are informational. Server and transport failures go through normal reporting rules. |
