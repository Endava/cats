---
hide_table_of_contents: true
---

# Pagination Limit Honored

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `PaginationLimitHonoredFuzzer` |
| **Log Key** | **PLHF** |
| **Description** | Verifies that `GET` collection endpoints declaring a numeric pagination limit query parameter (`limit`, `size`, `pageSize`, `per_page`, `maxResults`, `top`) actually honor `limit=1` in the returned JSON collection (OWASP API4:2023 Unrestricted Resource Consumption). |
| **Enabled by default?** | Yes (included in `compliance`, `ci`, and `full` profiles) |
| **Expected result** | `2XX` with at most `1` item in the returned JSON array or pagination envelope (`items`, `data`, `content`, `results`, `records`, `elements`, `values`, `entries`) |
| **Fuzzing logic** | Finds any numeric pagination limit query parameter whose minimum allows `1`, sends a `GET` request with that parameter set to `1`, and inspects the size of the returned JSON collection array. |
| **Conditions when this fuzzer will be skipped** | Non-`GET` operations or `GET` operations without a numeric pagination limit query parameter allowing `1`. |
| **HTTP methods that will be skipped** | `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `warn` (`Pagination limit ignored`) if the `2XX` response collection contains more than `1` item, `error` on `5XX`, and `info` when `0` or `1` items are returned. |
