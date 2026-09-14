--- 
hide_table_of_contents: true
---

# Recommended HTTP Codes

| Item                                                                | Description                                                                                                                          |
|:--------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | RecommendedHttpCodesLinterFuzzer                                                                                                     |
| **Log Key**                                                         | **RHCL**                                                                                                                             |
| **Description**                                                     | This fuzzer will check that each path element and http methods defines recommended HTTP response codes. See below table for details. |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                 |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Target contract elements** | OpenAPI contract metadata and structures relevant to this rule. |
| **Expected result** | `success` when no violation is found; `error` when the rule is violated. |
| **Fuzzing logic** | Inspects the OpenAPI contract without sending HTTP requests and reports violations of this rule. |
| **Conditions when this fuzzer will be skipped** | None; the linter runs once for the supplied contract. |
| **HTTP methods that will be skipped** | N/A — no HTTP requests are sent. |
| **Reporting**                                                       | Reports `error` if the http method from path does not have the recommended response codes, or `success` otherwise.                   | 


| HTTP Method | Recommended Response Codes List   |
|:------------|:----------------------------------|
| `POST`      | `400`, `500`, `2XX`               | 
| `PUT`       | `400`, `404`, `500`, `2XX`        | 
| `GET`       | `400`, `404`, `500`, `200`, `202` |
| `HEAD`      | `404`, `200`, `202`               |
| `DELETE`    | `400`, `404`, `500`, `2XX`        |
| `PATCH`     | `400`, `404`, `500`, `2XX`        |
| `TRACE`     | `400`, `500`, `200`               |
