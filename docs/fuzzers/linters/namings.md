--- 
hide_table_of_contents: true
---

# Namings

| Item                                                                | Description                                                                                                                                                                                                                             |
|:--------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | NamingsContractInfoFuzzer                                                                                                                                                                                                               |
| **Log Key**                                                         | **NCIF**                                                                                                                                                                                                                                |
| **Description**                                                     | This fuzzer will check if the OpenAPI specs follow consistent naming practices for path elements and JSON objects: must use plural naming for resources and naming is consistent for all elements (camelCase, snake_case, hyphen-case). |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command. You can also configure the naming conventions for each element like: path, path variables, query params, etc.                                                                      |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if at least one resource is not matching th naming conventions, or `success` otherwise.                                                                                                                                 | 