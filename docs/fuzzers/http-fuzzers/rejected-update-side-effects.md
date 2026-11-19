---
hide_table_of_contents: true
---

# Rejected Update Side Effects

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `RejectedUpdateSideEffectsFuzzer` |
| **Description** | Checks whether a rejected PUT or PATCH changed an owned resource. |
| **Enabled by default?** | Yes. Included in `security`, `compliance`, `ci`, and `full`. |
| **Expected result** | An invalid update is rejected and leaves the readable resource in its prior state. |
| **Reporting** | Reports an error for PATCH and a warning for PUT if a follow-up GET shows that the rejected update persisted a change. |
