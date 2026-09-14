---
sidebar_position: 91
description: Fuzz unconstrained OpenAPI arrays and objects
---

# Free-Form Fuzzers

OpenAPI schemas with unconstrained array items or unrestricted object
properties can hide unsafe parser and binding behavior. These fuzzers add
hostile values while preserving the rest of a generated request.

- `FreeFormArrayItemsFields` targets arrays without an item schema and sends hostile values, expecting either a valid `2XX` response or a validation `4XX` response.
- `FreeFormObjectFields` targets objects with unrestricted `additionalProperties` and adds hostile keys and values, expecting either `2XX` or `4XX`.

Both are Field Fuzzers and can be selected with `--fuzzers=FreeForm` or
excluded with `--skipFuzzers=FreeForm`.
