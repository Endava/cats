---
hide_table_of_contents: true
---

# Filter Parameter Honored

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `FilterParameterHonoredFuzzer` |
| **Description** | Checks whether a GET collection honors a declared exact-match filter. |
| **Enabled by default?** | Yes. Included in `compliance`, `ci`, and `full`. |
| **Expected result** | A comparable collection item reflects the requested filter value. |
| **Reporting** | Warns on returned items with a different comparable value. Results without comparable collection data are inconclusive. |
