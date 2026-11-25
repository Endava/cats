---
sidebar_position: 17
description: Masking Sensitive Headers, Query Parameters, and Body Fields
---

# Masking Sensitive Headers, Parameters, and Fields

To prevent accidental exposure of credentials in shared logs or report files (`.html` and `.json`), CATS automatically masks sensitive values by default:

- **Request headers** whose names match common credential patterns (`Authorization`, `Cookie`, `token`, `jwt`, `apiKey`, `secret`, `password`, `credential`, `appKey`, `appId`, or headers declared in `--wfcAuth`) are replaced with `$$HeaderName`.
- **Response headers** matching sensitive header names are replaced with `[REDACTED]`.
- **Query parameters** with sensitive names (`accessToken`, `idToken`, `authToken`, `apiKey`, `jwt`, `password`, `secret`, `signature`, `sig`, `token`), query parameters declared as OpenAPI `apiKey` security schemes (`in: query`), and WFC Auth query parameters are replaced with `$$QUERY_PARAM`.
- **`$$ENV_VAR` reference data and flow fields**: request body fields populated from `$$ENV_VAR` expressions (including embedded templates like `Bearer $$API_TOKEN`) retain their `$$ENV_VAR` placeholders in reports and logs, and echoed secret values in JSON responses are replaced with `[REDACTED]` (matched by value so unrelated endpoints sharing a generic field name are not over-masked).

Replacing request values with `$$VariableName` keeps the generated `TestXXX.json` files replayable via `cats replay` and `cats triage --verify`, and CATS generates a secret-free `replay.env.example` listing all required variables. Set those variables in the shell or in `./.env` (or pass `--envFile`), or override headers with `-H` and request body fields with `-R` (`--refDataParam`).

:::info Fuzzer mutations remain visible
When a fuzzer deliberately mutates a masked body field or sensitive query parameter (for example injecting `' OR 1=1--` into `password`), CATS leaves the fuzzed value visible in the report so you can see what triggered the failure. If a merge fuzzer keeps a real `$$ENV_VAR` secret inside the mutated string (such as leading spaces), only the secret substring is replaced with `$$ENV_VAR`.
:::

## Masking additional headers, parameters, and fields

CATS provides three explicit masking options depending on how narrowly you want to scope the mask:

- `--mask` — masks matching **headers, query parameters, and request/response body fields**.
- `--maskHeaders` — masks **HTTP headers only**.
- `--maskFields` — masks **request/response body fields only** (JSON and `application/x-www-form-urlencoded`), without touching HTTP headers (useful for body fields like `host`, `date`, `accept`, or `content-type`).

Body field masks support leaf names (`password`), parent objects (`credentials`, masking all descendant fields), and nested or array paths (`credentials.clientSecret`, `credentials#clientSecret`, `items[*].password`, `$.auth.secret`):

```shell
# Mask headers, query params, and body fields in one flag
cats --contract=YAML_FILE --server=SERVER_URL \
  -H "X-Custom-Tenant=mySecretTenant" \
  --mask "X-Custom-Tenant,password,credentials.clientSecret"

# Mask body fields only without affecting HTTP headers
cats --contract=YAML_FILE --server=SERVER_URL --maskFields "date,credentials,items[*].secret"

# Mask custom headers only
cats --contract=YAML_FILE --server=SERVER_URL -H "X-Custom-Tenant=mySecretTenant" --maskHeaders "X-Custom-Tenant"
```

Request values are replaced with replayable `$$VariableName` placeholders (`$$XCustomTenant`, `$$password`, `$$credentials_clientSecret`), while matching response headers and response body fields are replaced with `[REDACTED]`.

## Disabling automatic masking

When debugging locally and you want raw credentials written to console logs and reports, pass `--showSecrets`:

```shell
cats --contract=YAML_FILE --server=SERVER_URL -H "X-Api-Key=mySecretKey" --showSecrets
```

Names explicitly listed in `--mask`, `--maskHeaders`, or `--maskFields` are still masked even when `--showSecrets` is enabled.