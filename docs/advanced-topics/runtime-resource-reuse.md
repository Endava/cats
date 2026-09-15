---
sidebar_position: 8
description: Reuse identifiers learned from successful responses in later requests
---

# Runtime Resource Reuse

Starting with CATS 14.1.0, CATS keeps a run-local pool of resources observed in
successful API responses and uses their identifiers to make later requests more
likely to address real resources. This behavior is enabled by default and does
not make any additional HTTP requests.

CATS learns resources from `2XX` responses returned by:

- `POST` and `PUT` requests;
- collection `GET` requests, including arrays nested in a response object; and
- the response `Location` header when the body does not contain an identifier.

It can reuse matching identifiers in path parameters, query parameters, and
identifier-like request-body fields. CATS matches common names such as `id`,
`uuid`, `guid`, `petId`, `pet_id`, and qualified nested identifiers. A successful
`DELETE` invalidates the matching resource so that it is not reused later.

Explicit user data always takes precedence. Values supplied through `--refData`,
`--urlParams`, or `--queryParams` are not overwritten, and CATS preserves the
intent of fields deliberately changed by a fuzzer. Runtime reuse only lasts for
the current run; the pool is cleared before a new run starts.

When CATS applies a learned value, the individual test report records the source
request and response location, the target request field, and the reused value.
The summary report also shows whether runtime resource reuse was enabled.

To disable the feature for a run, use:

```shell
cats -c contract.yml -s http://localhost:8080 --no-reuseSuccessfulResources
```

The `FunctionalFuzzer` has explicit test-to-test correlation through its
[`output` variables](../fuzzers/special-fuzzers/functional-fuzzer#correlating-tests).
Use those variables when a functional flow needs to pass response values to a
later request.
