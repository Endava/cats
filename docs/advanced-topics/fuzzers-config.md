---
sidebar_position: 7
description: Customizing the Default Expected HTTP Response Code for Fuzzers
---

When you explore the [Fuzzers](https://endava.github.io/cats/docs/fuzzers/) page, you'll notice that each fuzzer is associated 
with an expected HTTP response code. 
The fuzzer compares the received HTTP response code with the expected one and generates a report based on the match.

In certain scenarios, you may want to customize the default values expected by fuzzers. 
You can achieve this by providing a properties file using the `--fuzzersConfig` argument. 
In this file, the keys correspond to the fuzzer names and configuration names, while the values represent the desired HTTP code to expect.
At the moment, the only supported configuration is `expectedResponseCode`.

This is an example (file name `fuzzConfig.properties`):

```properties
DummyAcceptHeaders.expectedResponseCode=403
```

When passing this file to `cats` using:

```shell
cats -c petstore.yml -s http://localhost:8080 --fuzzersConfig fuzzConfig.properties
```

CATS will interpret `403` as expected response code for the `DummyAcceptHeaders` fuzzer, overriding the default value of `406`.
