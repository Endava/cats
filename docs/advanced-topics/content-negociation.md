---
sidebar_position: 2
---

# Content Negotiation
Some APIs might use content negotiation versioning which implies formats like `application/v11+json` in the `Accept` header.

You can handle this in CATS as follows:

- if the OpenAPI contract defines its content as:

```yaml
 requestBody:
        required: true
        content:
          application/v5+json:
            schema:
              $ref: '#/components/RequestV5'
          application/v6+json:
            schema:
              $ref: '#/components/RequestV6'
```

by having clear separation between versions, you can pass the `--contentType` argument with the version you want to test: `cats ... --contentType="application/v6+json"`.

If the OpenAPI contract is not version aware (you already exported it specific to a version) and the content looks as:

```yaml
 requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/RequestV5'
```
and you still need to pass the `application/v5+json` `Accept` header, you can use the `--headers` file to add it:

```yaml
all:
  Accept: "application/v5+json"
``
