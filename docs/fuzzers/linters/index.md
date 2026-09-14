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

CATS has currently 41 registered `Linter` Fuzzers or Linters. The names below
match the current output of `cats list --linters`:
- `ArrayWithoutItemsLinter` - detects array schemas that do not define an `items` property
- `CollectionPaginationLinter` - verifies that collection GET operations support pagination
- `DeleteHasBodyLinter` - checks if DELETE methods have a body
- `EmptyPathsLinter` - verifies that each path contains at least one operation
- `EmptyRequestSchemaLinter` - detects request schemas without properties, references, or composition
- `EmptyResponseSchemaLinter` - detects response schemas without properties, references, or composition
- `EnumCaseGlobalLinter` - verifies consistent casing for string enum values across the contract
- `EnumCasePathLevelLinter` - verifies enum casing for inline path-level schemas
- `GetHasBodyLinter` - checks if GET methods have a body
- `HeadHasBodyLinter` - checks if HEAD methods have a body
- `HeadersCaseLinter` - verifies that HTTP headers follow naming conventions
- `HttpMethodConsistencyErrorLinter` - flags missing critical REST methods
- `HttpMethodConsistencyWarnLinter` - flags missing optional REST methods
- `HttpStatusCodeInRangeLinter` - verifies that response codes are between 100 and 599
- `JsonObjectsCaseLinter` - verifies that JSON elements follow naming conventions
- `MultipleSuccessCodesLinter` - flags operations with multiple 2xx response codes
- `OperationIdVerbPrefixLinter` - verifies operationId prefixes for HTTP methods
- `PatchWithoutBodyLinter` - verifies that PATCH operations define a request body
- `PathCaseLinter` - verifies that path elements follow naming conventions
- `PathNounsLinter` - verifies that path elements use nouns
- `PathPluralsLinter` - verifies plural resource names
- `PathTagsLinter` - verifies path tags and top-level tag consistency
- `PostWithoutBodyLinter` - verifies that POST operations define a request body
- `PutWithoutBodyLinter` - verifies that PUT operations define a request body
- `QueryParamsCaseLinter` - verifies that query parameters follow naming conventions
- `RecommendedHttpCodesLinter` - verifies recommended response codes
- `ResponsesWithBodiesLinter` - verifies response bodies are present except for 204 and 304
- `SecuritySchemesLinter` - verifies valid security schemes are configured
- `StringSchemaLimitGlobalLinter` - verifies string schemas have `maxLength` or `enum`
- `StringSchemaPathLevelLinter` - verifies limits for inline path-level string schemas
- `TopLevelElementsLinter` - verifies meaningful contract-level metadata
- `TracingHeadersLinter` - verifies recommended tracing headers
- `UniqueOperationIdsLinter` - verifies that operationIds are unique
- `UnusedExamplesLinter` - flags unreferenced component examples
- `UnusedHeadersLinter` - flags unreferenced component headers
- `UnusedParametersLinter` - flags unreferenced component parameters
- `UnusedRequestBodiesLinter` - flags unreferenced component request bodies
- `UnusedResponsesLinter` - flags unreferenced component responses
- `UnusedSchemasLinter` - flags unreferenced component schemas
- `VersionsLinter` - verifies that paths do not contain versioning information
- `XmlContentTypeLinter` - verifies that requests and responses do not offer `application/xml`

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
