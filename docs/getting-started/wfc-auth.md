---
sidebar_position: 22
description: Use Web Fuzzing Commons authentication configurations with CATS
---

# Web Fuzzing Commons Authentication

CATS can load authentication configurations in the Web Fuzzing Commons format
with `--wfcAuth`. Select a named entry with `--wfcAuthName`; if no name is
provided, the first entry is used.

```bash
cats -c openapi.yml -s http://localhost:8080 \
  --wfcAuth=wfc-auth.yml --wfcAuthName=login-bearer
```

A static authentication entry can provide fixed headers:

```yaml
auth:
  - name: static-api-key
    fixedHeaders:
      - name: X-API-Key
        value: change-me
```

For a login flow, define the endpoint and credentials, then extract the token
from the response and send it with later requests:

```yaml
auth:
  - name: login-bearer
    loginEndpointAuth: {}

authTemplate:
  loginEndpointAuth:
    endpoint: /login
    verb: POST
    contentType: application/json
    payloadUserPwd:
      usernameField: username
      username: cats
      passwordField: password
      password: secret
    token:
      extractFrom: body
      extractSelector: /accessToken
      sendIn: header
      sendName: Authorization
      sendTemplate: Bearer {token}
```

WFC authentication can provide headers, cookies, or query parameters. It is
also available to `cats replay`, `cats run`, and `cats random`.
