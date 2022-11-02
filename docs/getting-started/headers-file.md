---
sidebar_position: 10
description: Supplying custom headers to CATS
---

# Headers File
The headers file can be used to send fixed-value headers. The most common use case is authentication headers. 
Headers can be path-specific or applicable to all paths using the `all` element. Specific paths will take precedence over the `all` element.

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
