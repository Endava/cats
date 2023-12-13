---
sidebar_position: 17
description: Masking Authentication Headers
---

# Masking Authentication Headers

While executing CATS without utilizing production credentials, it is advisable to refrain from incorporating authentication headers within report
files, encompassing both .html and .json formats.
This precautionary measure is essential as test cases could become appended to bug reports, potentially leading to the inadvertent exposure of
sensitive credentials to unintended recipients.

You can use the `--maskHeaders` argument to provide a list of headers to be masked. 
The masking process will replace the values of those headers with `$$HeaderName`. 
This will allow the test cases to be replay-able through `cats replay` by setting a local environment variable
called `HeaderName` and setting it with the sensitive value. 

:::note
The `curl` equivalent of running the test case will also use the `$Header-Name` variable.
:::

```shell
cats --contract=YAML_FILE --server=SERVER_URL -H "X-Api-Key=mySecretKey"  --maskHeaders "X-Api-Key"
```

This will replace `mySecretKey` with `$$XApiKey` in all report files.