---
hide_table_of_contents: true
---

# Content-Type Parameter Differentials

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ContentTypeParameterDifferentialHeadersFuzzer` |
| **Description** | Checks how request media-type parsers handle conflicting charset or boundary parameters on operations with request bodies. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | A controlled HTTP response without a server failure. |
| **Reporting** | Controlled `2XX`, `3XX`, and `4XX` responses are informational. Server and transport failures go through normal reporting rules. |
