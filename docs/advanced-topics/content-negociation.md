---
sidebar_position: 2
description: Dealing with content negotiation versioning
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
```

## Supported payload formats and `Accept` negotiation

By default, CATS supports request bodies declared with:
- `application/json` and `*+json` media types
- `application/x-www-form-urlencoded`
- `application/xml`, `text/xml`, and `application/*+xml` (serialized using OpenAPI Schema `xml` metadata including element names, namespaces, prefixes, attributes, and wrapped arrays)
- `text/plain`
- `application/x-ndjson` and `application/ndjson`
- `multipart/form-data`, with the selection rules described below

### Selecting the request content type

When an operation declares more than one request content type, CATS picks the first of its preferred types that the operation declares. By default, the order is JSON, `application/x-www-form-urlencoded`, XML, `text/plain`, and NDJSON. `multipart/form-data` is not part of this list, so an operation that declares both `application/json` and `multipart/form-data` is fuzzed using JSON. An operation that declares `multipart/form-data` and no other supported type is fuzzed as multipart.

If you supply `--contentType`, only that type is considered. Use `--contentType="multipart/form-data"` to fuzz the multipart variant of operations that also declare other types. Operations that don't declare the supplied type are not selected, so multipart-only operations are not fuzzed when `--contentType` is set to a different type.

Each property of a multipart request body is sent as a part. Properties declared as `type: string` and `format: binary` are sent as files (see [Reference Data](../getting-started/reference-data-file.md#sending-files-in-multipart-requests)).

For `Accept` headers, CATS automatically negotiates a media type declared under the operation's `2XX` responses in the OpenAPI contract when present, falling back to the default content type (`application/json`) otherwise.
