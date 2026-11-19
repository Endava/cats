---
hide_table_of_contents: true
---

# Encoded Authentication Headers

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `EncodedAuthenticationHeadersFuzzer` |
| **Description** | Checks decoder robustness on operations using declared or configured Basic or JWT authentication. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, `crash`, and `full`. |
| **Expected result** | A controlled HTTP response without a server failure. |
| **Reporting** | Controlled `2XX`, `3XX`, and `4XX` responses are informational. Server and transport failures go through normal reporting rules. |
