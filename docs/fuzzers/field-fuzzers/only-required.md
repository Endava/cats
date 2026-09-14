---
sidebar_position: 93
description: Test requests containing only required fields
---

# OnlyRequiredFields

This fuzzer generates a request containing only fields marked `required` in
the OpenAPI contract. A valid API should accept the minimal request and return
a `2XX` response. It is included in the `quick`, `ci`, and `full` profiles.

```bash
cats -c openapi.yml -s http://localhost:8080 --fuzzers=OnlyRequiredFields
```
