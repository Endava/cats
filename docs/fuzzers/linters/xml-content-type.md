--- 
hide_table_of_contents: true
---

# XML Content Type

| Item                                                                | Description                                                                                                        |
|:--------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | XmlContentTypeLinterFuzzer                                                                                         |
| **Log Key**                                                         | **XCTL**                                                                                                           |
| **Description**                                                     | This fuzzer will check that each path element and HTTP method does not accept `application/xml` as `Content-Type`. |
| **Enabled by default?**                                             | No. You need to use the `cats lint ...` sub-command.                                                               |                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Reporting**                                                       | Reports `error` if the path and HTTP method accepts `application/xml`, or `success` otherwise.                     | 
