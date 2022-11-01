---
sidebar_position: 12
description: What types of authentication are supported by CATS
---

# API Authentication

:::danger
CATS outputs authentication headers in plain text in both logs and report files. 
Make sure you remove those when sharing/archiving/uploading the report files or logs.
You can leverage environment variables by setting `$$env_variable` as values.
:::

## HTTP header(s) based authentication
CATS supports any form of HTTP header(s) based authentication (basic auth, oauth, custom JWT, apiKey, etc) using the [headers](#headers-file) mechanism or using `-H header=value` arguments.

:::tip
When using the `--headerFile` make sure the specific HTTP header name and value is applied to `all` endpoints.
:::

Additionally, basic auth is also supported using the `--basicauth=USR:PWD` argument.

## Refreshing the access credentials
If the authentication token needs periodical refresh, or if you want to provide it dynamically, you can encapsulate the provisioning of the authentication header in a script and use the following syntax:

```bash
cats --contract=api.yml --server=http://localhost:8000 -H "Authorization=auth_script" --authRefreshScript="./get_token.sh" --authRefreshInterval 300
```

This will use the `get_token.sh` script to get the value for the `Authorization` header and will refresh its value by calling the `get_tokne.sh` script every 300 seconds.

:::caution
Please note that the output of the `get_token.sh` script will be copied as raw data in the header value so make sure you remove any formatting or include needed prefixes (like `Bearer` for example for JWTs).
:::

## One-Way or Two-Way SSL
By default, CATS trusts all server certificates and doesn't perform hostname verification.

For two-way SSL you can specify a JKS file (Java Keystore) that holds the client's private key using the following arguments:
- `--sslKeystore` Location of the JKS keystore holding certificates used when authenticating calls using one-way or two-way SSL
- `--sslKeystorePwd` The password of the `sslKeystore`
- `--sslKeyPwd` The password of the private key within the `sslKeystore`

For details on how to load the certificate and private key into a Java Keystore you can use this guide: [https://mrkandreev.name/blog/java-two-way-ssl/](https://mrkandreev.name/blog/java-two-way-ssl/).
