# Linters

Linters are also called `Linter` Fuzzers.

Usually a good OpenAPI contract must follow several good practices in order to make it easy digestible by the service clients and act as much as possible as self-sufficient documentation:
- follow good and **consistent** practices for naming the contract elements like paths, query params, headers, requests, responses
- always use plural for the resources
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

CATS has currently 17 registered `Linter` Fuzzers or Linters:
- `EmptyPathsLinter` - verifies that the current path contains at least one operation
- `HeadersCaseLinter` - verifies that HTTP headers follow naming conventions
- `HttpStatusCodeInValidRangeFuzzer` -  verifies that all HTTP response codes are within the range of 100 to 599
- `JsonObjectsCaseLinter` - verifies that JSON elements follow naming conventions
- `PathCaseLinter` - verifies that path elements follow naming conventions
- `PathNounsLinter` - verifies that path elements use nouns to describe resources
- `PathPluralsLinter` - verifies that path elements uses pluralization to describe resources
- `PathTagsLinterFuzzer` - verifies that all OpenAPI paths contain tags elements and checks if the tags elements match the ones declared at the top level
- `QueryParamsCaseLinter` - verifies that query params follow naming conventions
- `RecommendedHeadersLinterFuzzer` - verifies that all OpenAPI contract paths contain recommended headers like: CorrelationId/TraceId, etc.
- `RecommendedHttpCodesLinterFuzzer` - verifies that the current path contains all recommended HTTP response codes for all operations
- `SecuritySchemesLinterFuzzer` - verifies if the OpenApi contract contains valid security schemas for all paths, either globally configured or per path
- `TopLevelElementsLinterFuzzer` - verifies that all OpenAPI contract level elements are present and provide meaningful information: API description, documentation, title, version, etc.
- `VersionsLinterFuzzer` - verifies that a given path doesn't contain versioning information
- `XmlContentTypeLinterFuzzer` - verifies that all OpenAPI contract paths responses and requests does not offer `application/xml` as a Content-Type
- `TracingHeadersLinter` - verifies that all OpenAPI contract paths contain recommended headers like: CorrelationId/TraceId, etc.
- `UniqueOperationIdsLinter` - verifies that all operationIds are unique

You can run only these Fuzzers using `cats lint --contract=CONTRACT`.

Naming conventions can be configured using the following arguments:
- `--headersNaming=<headersNaming>` Naming strategy for json object properties. Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER`. Default: `HTTP_HEADER`
- `--jsonObjectsNaming=<jsonObjectsNaming>` Naming strategy for json objects. Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER`. Default: `PASCAL`
- `--jsonPropertiesNaming=<jsonPropertiesNaming>` Naming strategy for json object properties. Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER`. Default: `CAMEL`
- `--pathNaming=<pathNaming>` Naming strategy for paths (excluding path variables). Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER`. Default: `KEBAB`
- `--pathVariablesNaming=<pathVariablesNaming>` Naming strategy for paths variables. Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER`. Default: `CAMEL`
- `--queryParamsNaming=<queryParamsNaming>` Naming strategy for query parameters. Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER`. Default: `SNAKE`


:::info
`Linter` Fuzzers are disabled by default. You must either use the `cats lint ...` command to run only the linters or
the `--includeLinters` argument to run them along other Fuzzers.
:::

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```