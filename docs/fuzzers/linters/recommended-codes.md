--- 
hide_table_of_contents: true
---

# Recommended HTTP Codes

| Item                                                                | Description                                                                                                                              |
|:--------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | RecommendedHttpCodesContractInfoFuzzer                                                                                                   |
| **Log Key**                                                         | **RHCCIF**                                                                                                                               |
| **Description**                                                     | This fuzzer will check that each path element and http methods defines recommended HTTP response codes. See below table for details.     |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                                                     |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if the http method from path does not have the recommended response codes, or `success` otherwise.                       | 


| HTTP Method | Recommended Response Codes List   |
|:------------|:----------------------------------|
| `POST`      | `400`, `500`, `2XX`               | 
| `PUT`       | `400`, `404`, `500`, `2XX`        | 
| `GET`       | `400`, `404`, `500`, `200`, `202` |
| `HEAD`      | `404`, `200`, `202`               |
| `DELETE`    | `400`, `404`, `500`, `2XX`        |
| `PATCH`     | `400`, `404`, `500`, `2XX`        |
| `TRACE`     | `400`, `500`, `200`               |
