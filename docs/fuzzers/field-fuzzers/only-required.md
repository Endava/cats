---
sidebar_position: 93
description: Test requests containing only required fields
---

# OnlyRequiredFields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `OnlyRequiredFieldsFuzzer` |
| **Log Key** | N/A; the fuzzer name and mutation scenario identify the test. |
| **Description** | Sends a request containing only fields marked as required in the OpenAPI contract. |
| **Enabled by default?** | Yes; it is included in the `quick`, `ci`, `security`, `compliance`, `type-coercion`, `coverage`, `crash`, and `full` profiles. |
| **Target field types** | Request body fields and optional query parameters removed from the generated request. |
| **Expected result** | `2XX`. |
| **Fuzzing logic** | Removes optional fields (and unmarked server-generated metadata when no fields are marked required) and sends the minimal valid request. If the strict required-only request does not return `2XX`, it automatically tries up to 12 minimal fallback payloads containing defaulted or minimal optional fields. |
| **Conditions when this fuzzer will be skipped** | None beyond the normal request-generation and HTTP-method eligibility checks. |
| **HTTP methods that will be skipped** | None. For methods without a body, the minimal request is applied to query parameters. |
| **Reporting** | Reports a documented `2XX` response as success; unexpected response codes, schema mismatches, and server errors follow normal CATS reporting. |

This fuzzer generates a request containing only fields marked `required` in
the OpenAPI contract. A valid API should accept the minimal request and return
a `2XX` response. It runs in the initial seed wave alongside `HappyPath` when
runtime resource reuse is enabled, and is included in the `quick`, `ci`,
`security`, `compliance`, `type-coercion`, `coverage`, `crash`, and `full` profiles.

```bash
cats -c openapi.yml -s http://localhost:8080 --fuzzers=OnlyRequiredFields
```
