---
sidebar_position: 17
description: Masking Authentication Headers
---

# Masking Sensitive Headers and Parameters

To prevent accidental exposure of credentials in shared logs or report files (`.html` and `.json`), CATS automatically masks sensitive headers and query parameters by default:

- **Request headers** whose names match common credential patterns (`Authorization`, `Cookie`, `token`, `jwt`, `apiKey`, `secret`, `password`, `credential`, `appKey`, `appId`, or headers declared in `--wfcAuth`) are replaced with `$$HeaderName`.
- **Response headers** matching sensitive header names are replaced with `[REDACTED]`.
- **Query parameters** with sensitive names (`accessToken`, `idToken`, `authToken`, `apiKey`, `jwt`, `password`, `secret`, `signature`, `sig`, `token`), query parameters declared as OpenAPI `apiKey` security schemes (`in: query`), and WFC Auth query parameters are replaced with `$$QUERY_PARAM`.

Replacing request values with `$$VariableName` keeps the generated `TestXXX.json` files replayable via `cats replay`: set the corresponding environment variable in the shell or in `./.env` (or pass `--envFile`), or override the header with `-H`.

## Masking additional headers

Use `--maskHeaders` to mask custom header names in addition to the built-in sensitive set:

```shell
cats --contract=YAML_FILE --server=SERVER_URL -H "X-Custom-Tenant=mySecretTenant" --maskHeaders "X-Custom-Tenant"
```

This replaces `mySecretTenant` with `$$XCustomTenant` in console output and all report files.

## Disabling automatic masking

When debugging locally and you want raw credentials written to console logs and reports, pass `--showSecrets`:

```shell
cats --contract=YAML_FILE --server=SERVER_URL -H "X-Api-Key=mySecretKey" --showSecrets
```

Headers explicitly listed in `--maskHeaders` are still masked even when `--showSecrets` is enabled.