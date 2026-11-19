---
hide_table_of_contents: true
---

# Misleading Create PUT

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `MisleadingCreatePutFuzzer` |
| **Description** | Checks the status of a PUT on a resource created during the run and confirmed to exist via GET. |
| **Enabled by default?** | Yes. Included in `security`, `ci`, and `full`. |
| **Expected result** | The update succeeds without reporting `201 Created`. |
| **Reporting** | Reports an error if PUT reports `201 Created` for the existing resource. |
