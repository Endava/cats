---
hide_table_of_contents: true
---

# InsertWhitespacesInFieldNamesField

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `InsertWhitespacesInFieldNamesFieldFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Inserts random Unicode whitespace characters into request field names. |
| **Enabled by default?** | Yes. |
| **Target field types** | JSON request body fields. |
| **Expected result** | `4XX`, because the mutated field name should not be accepted as the documented field. |
| **Fuzzing logic** | Iterates over request fields, inserts two random Unicode whitespace characters into each field name, and sends the mutated payload. |
| **Conditions when this fuzzer will be skipped** | When the request payload is empty or a candidate field is not present in the JSON body. |
| **HTTP methods that will be skipped** | `HEAD`, `GET`, `DELETE`. |
| **Reporting** | Reports a rejected field-name mutation as the expected result; unexpected response codes and server errors follow normal CATS reporting. |
