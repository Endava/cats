# Linters

Linters are also called `ContractInfo` Fuzzers.

Usually a good OpenAPI contract must follow several good practices in order to make it easy digestible by the service clients and act as much as possible as self-sufficient documentation:
- follow good and **consistent** practices for naming the contract elements like paths, requests, responses
- always use plural for the path names, separate paths words through hyphens/underscores, use camelCase or snake_case for any `json` types and properties
- provide tags for all operations in order to avoid breaking code generation on some languages and have a logical grouping of the API operations
- provide good description for all paths, methods and request/response elements
- provide meaningful responses for `POST`, `PATCH` and `PUT` requests
- provide examples for all requests/response elements
- provide structural constraints for (ideally) all request/response properties (min, max, regex)
- have some sort of `CorrelationIds/TraceIds` headers for traceability
- have at least a security schema in place
- avoid having the API version part of the paths
- document response codes for both "happy" and "unhappy" flows
- avoid using `xml` payload unless there is a really good reason (like documenting an old API for example)
- json types and properties do not use the same naming (like having a `Pet` with a property named `pet`)

CATS has currently 9 registered `ContractInfo` Fuzzers or Linters:
- `HttpStatusCodeInValidRangeFuzzer` -  verifies that all HTTP response codes are within the range of 100 to 599
- `NamingsContractInfoFuzzer` - verifies that all OpenAPI contract elements follow REST API naming good practices
- `PathTagsContractInfoFuzzer` - verifies that all OpenAPI paths contain tags elements and checks if the tags elements match the ones declared at the top level
- `RecommendedHeadersContractInfoFuzzer` - verifies that all OpenAPI contract paths contain recommended headers like: CorrelationId/TraceId, etc.
- `RecommendedHttpCodesContractInfoFuzzer` - verifies that the current path contains all recommended HTTP response codes for all operations
- `SecuritySchemesContractInfoFuzzer` - verifies if the OpenApi contract contains valid security schemas for all paths, either globally configured or per path
- `TopLevelElementsContractInfoFuzzer` - verifies that all OpenAPI contract level elements are present and provide meaningful information: API description, documentation, title, version, etc.
- `VersionsContractInfoFuzzer` - verifies that a given path doesn't contain versioning information
- `XmlContentTypeContractInfoFuzzer` - verifies that all OpenAPI contract paths responses and requests does not offer `application/xml` as a Content-Type

You can run only these Fuzzers using `cats lint --contract=CONTRACT`.

:::info
`ContractInfo` Fuzzers are disabled by default. You must either use the `cats lint ...` command to run only the linters or
the `--includeContract` argument to run them along other Fuzzers.
:::

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```