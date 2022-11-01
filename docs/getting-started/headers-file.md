---
sidebar_position: 10
description: Supplying custom headers to CATS
---

# Headers File
This can be used to send custom fixed headers with each payload. It is useful when you have authentication tokens you want to use to authenticate the API calls. You can use path specific headers or common headers that will be added to each call using an `all` element. Specific paths will take precedence over the `all` element.
Sample headers file:

```yaml
all:
    Accept: application/json
/path/0.1/auth:
    jwt: XXXXXXXXXXXXX
/path/0.2/cancel:
    jwt: YYYYYYYYYYYYY
```

This will add the `Accept` header to all calls and the `jwt` header to the specified paths. 

You can use environment (system) variables in a headers file using: `$$VARIABLE_NAME`.

:::caution
Notice double `$$` when supplying environment variables.
:::
