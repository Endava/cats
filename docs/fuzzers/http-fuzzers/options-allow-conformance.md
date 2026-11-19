---
hide_table_of_contents: true
---

# OPTIONS Allow Conformance

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `OptionsAllowConformanceFuzzer` |
| **Description** | Checks the Allow header from one OPTIONS request per contract path. |
| **Enabled by default?** | Yes. Included in `security`, `compliance`, `ci`, and `full`. |
| **Expected result** | A successful response with an Allow header consistent with the path’s declared methods. |
| **Reporting** | Warns if Allow omits required methods or lists unexpected methods, reports a server error on `5XX`, and treats a missing Allow header as inconclusive. |
