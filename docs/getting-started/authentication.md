---
sidebar_position: 12
description: Types of authentication supported by CATS
---

# API Authentication

:::info
By default, CATS automatically masks sensitive request headers (such as `Authorization`, `Cookie`, `X-API-Key`, tokens, secrets, and passwords) and sensitive query parameters (including OpenAPI `in: query` API keys and WFC Auth query parameters) in console logs and report files.
Masked request headers and query parameters are replaced with `$$VariableName` placeholders so that test cases remain replayable with environment variables or a `.env` file, while sensitive response headers are replaced with `[REDACTED]`.
Use `--maskHeaders` to mask additional custom headers, or `--showSecrets` to disable automatic masking.
:::

## Environment variables and `.env` files
You can reference environment variables in headers, query parameters, and reference data using `$$VARIABLE_NAME`. CATS checks the process environment first and automatically loads fallback variables from `./.env` when present.

Use `--envFile=FILE` to load variables from a custom dotenv file, or `--noEnvFile` (`--no-env-file`) to disable automatic `./.env` loading.

## HTTP header(s) based authentication
CATS supports any form of HTTP header(s) based authentication (basic auth, oauth, custom JWT, apiKey, etc) using the [headers](headers-file) mechanism or using `-H header=value` arguments.

:::tip
When using the `--headers` file make sure the specific authentication header is applied to `all` endpoints.
:::

Additionally, basic auth is also supported using the `--basicAuth=USR:PWD` (or `--basicauth`) argument.

## Refreshing the access credentials
If the authentication token needs periodical refresh, or if you want to provide it dynamically, you can encapsulate the provisioning of the authentication header in a script and use the following syntax:

```bash
cats --contract=api.yml --server=http://localhost:8000 -H "Authorization=auth_script" --authRefreshScript="./get_token.sh" --authRefreshInterval 300
```

This will use the `get_token.sh` script to get the value for the `Authorization` header and will refresh its value by calling the `get_token.sh` script every 300 seconds.

:::caution
Please note that the output of the `get_token.sh` script will be copied as raw data in the header value so make sure you remove any formatting or include needed prefixes (like `Bearer` for example for JWTs).
:::

## TLS Verification and Mutual TLS

:::info
By default, CATS verifies server TLS certificates and hostnames using the system trust store. To disable TLS certificate and hostname verification for trusted test environments using self-signed certificates, pass `--insecure`.
:::

For mutual TLS (two-way SSL) you can specify a JKS file (Java Keystore) that holds the client's certificate and private key using the following arguments:
- `--sslKeystore` Location of the JKS keystore holding the client certificate and private key for mutual TLS
- `--sslKeystorePwd` The password of the `sslKeystore`
- `--sslKeyPwd` The password of the private key within the `sslKeystore`

For details on how to load the certificate and private key into a Java Keystore you can use this guide: [https://mrkandreev.name/blog/java-two-way-ssl/](https://mrkandreev.name/blog/java-two-way-ssl/).
