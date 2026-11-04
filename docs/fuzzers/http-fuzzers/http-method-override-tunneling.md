---
hide_table_of_contents: true
---

# HTTP Method Override Tunneling

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `HttpMethodOverrideTunnelingFuzzer` |
| **Log Key** | **HMOTF** |
| **Description** | Sends documented `POST` requests carrying HTTP method-override headers (`X-HTTP-Method-Override`, `X-Method-Override`, `X-HTTP-Method`) set to an undocumented verb (such as `DELETE`, `PUT`, `PATCH`, or `CATS_OVERRIDE`) to detect OWASP API5:2023 HTTP verb tunneling and method-override filter crashes. |
| **Enabled by default?** | Yes (included in `security`, `compliance`, `ci`, `crash`, and `full` profiles) |
| **Expected result** | `4XX` or `2XX` (executed as the documented `POST`, never dispatched to an undocumented `DELETE`) |
| **Fuzzing logic** | For each path supporting `POST`, selects an HTTP verb not documented on that path (preferring `DELETE`, `PUT`, `PATCH`, or `CATS_OVERRIDE`) and sends one `POST` request per override header (`X-HTTP-Method-Override`, `X-Method-Override`, `X-HTTP-Method`). |
| **Conditions when this fuzzer will be skipped** | Non-`POST` operations or paths already tested in the current run. |
| **HTTP methods that will be skipped** | `GET`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` (`HTTP method override accepted`) if a `POST` with `X-*-Method-Override: DELETE` returns `204 No Content` on a path where `DELETE` is undocumented, or `Server error` on `5XX`. |
