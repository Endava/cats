---
hide_table_of_contents: true
---

# Partial Update PUT

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `PartialUpdatePutFuzzer` |
| **Description** | Checks replacement semantics for PUT on a resource created during the run. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, and `full`. |
| **Expected result** | A subsequent GET reflects the replacement of the resource, including the handling of omitted optional fields. |
| **Reporting** | Warns when an omitted optional field retains its previous value or when comparable writable fields differ from the update. |
