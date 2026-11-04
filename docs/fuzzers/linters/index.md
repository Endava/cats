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

CATS has currently 58 registered `Linter` Fuzzers or Linters. The names below
match the current output of `cats list --linters`:
- `ArraySchemaLimitGlobalLinter` - verifies that all global array schemas specify maxItems
- `ArraySchemaLimitPathLevelLinter` - verifies that array schemas specify maxItems for inline schemas of the current path/method
- `ArrayWithoutItemsLinter` - detects array schemas that do not define an `items` property
- `AuthResponseCodesConsistencyLinter` - verifies that secured operations document 401/403 error responses and public operations opting out of security (security: []) do not document 401
- `CollectionPaginationLinter` - verifies that collection GET operations support pagination
- `ContradictorySchemaConstraintsLinter` - detects contradictory or invalid schema constraints (min > max bounds, invalid regex patterns, undeclared required fields, and invalid enum defaults/examples)
- `DeleteHasBodyLinter` - checks if DELETE methods have a body
- `EmptyPathsLinter` - verifies that each path contains at least one operation
- `EmptyRequestSchemaLinter` - detects request schemas without properties, references, or composition
- `EmptyResponseSchemaLinter` - detects response schemas without properties, references, or composition
- `EnumCaseGlobalLinter` - verifies consistent casing for string enum values across the contract
- `EnumCasePathLevelLinter` - verifies enum casing for inline path-level schemas
- `ErrorResponseConsistencyLinter` - verifies that 4xx and 5xx error responses across the contract share a consistent structured error schema
- `GetHasBodyLinter` - checks if GET methods have a body
- `HeadHasBodyLinter` - checks if HEAD methods have a body
- `HeadersCaseLinter` - verifies that HTTP headers follow naming conventions
- `HttpMethodConsistencyLinter` - flags missing REST HTTP methods (POST on collection paths and GET, PUT/PATCH, DELETE on item paths) across resource groups
- `HttpStatusCodeInRangeLinter` - verifies that response codes are between 100 and 599
- `JsonObjectsCaseLinter` - verifies that JSON elements follow naming conventions
- `MissingOperationMetadataLinter` - verifies that every operation defines a non-blank operationId and at least one of summary or description
- `MultipleSuccessCodesLinter` - flags operations with multiple 2xx response codes
- `NumericSchemaBoundsGlobalLinter` - verifies that all global numeric schemas specify minimum and maximum bounds or enum
- `NumericSchemaBoundsPathLevelLinter` - verifies that numeric schemas specify minimum and maximum bounds or enum for inline schemas of the current path/method
- `OneOfAmbiguousVariantsLinter` - detects oneOf schemas without a discriminator where one variant accepts every instance of another variant, making payloads match more than one variant
- `OperationIdVerbPrefixLinter` - verifies operationId prefixes for HTTP methods
- `PaginationConsistencyLinter` - verifies that paginated GET operations across the contract use consistent pagination parameter names and return pagination metadata rather than a bare array
- `PatchWithoutBodyLinter` - verifies that PATCH operations define a request body
- `PathCaseLinter` - verifies that path elements follow naming conventions
- `PathNounsLinter` - verifies that path elements use nouns and custom action segments are terminal and nested under a resource
- `PathParameterDeclarationLinter` - verifies that URL path template variables (`{param}`) match declared `in: path` parameters and have `required: true`
- `PathPluralsLinter` - verifies plural collection resource names while allowing singular singletons and terminal custom operations
- `PathTagsLinter` - verifies path tags and top-level tag consistency
- `PathVariableConsistencyLinter` - verifies that path variables use consistent names per resource segment and consistent schema types/formats across all operations
- `PolymorphismDiscriminatorLinter` - validates that oneOf/anyOf discriminator definitions specify a non-blank propertyName present in sub-schemas and valid mapping references
- `PostWithoutBodyLinter` - verifies that POST operations define a request body
- `PropertySemanticsConsistencyLinter` - verifies that audit timestamp properties share a consistent type/format across schemas and flags floating-point monetary fields
- `PutWithoutBodyLinter` - verifies that PUT operations define a request body
- `QueryParamsCaseLinter` - verifies that query parameters follow naming conventions
- `RecommendedHttpCodesLinter` - verifies recommended response codes
- `ResourceSchemaSymmetryLinter` - verifies that properties shared between write request schemas (POST/PUT/PATCH) and GET response schemas within a resource group have consistent types and formats
- `ResponsesWithBodiesLinter` - verifies response bodies are present except for 204 and 304
- `SchemaValueConformanceLinter` - detects default and example values that violate the type, format, length, range, pattern or item constraints of their own schema
- `SecuritySchemesLinter` - verifies valid security schemes are configured
- `SensitiveDataInQueryLinter` - flags query parameters whose names indicate credentials, secrets, or sensitive PII (such as password, secret, apiKey, accessToken, ssn, cvv, cardNumber)
- `StringSchemaLimitGlobalLinter` - verifies string schemas have `maxLength` or `enum`
- `StringSchemaPathLevelLinter` - verifies limits for inline path-level string schemas
- `TopLevelElementsLinter` - verifies meaningful contract-level metadata
- `TracingHeadersLinter` - verifies recommended tracing headers
- `UnconstrainedAdditionalPropertiesLinter` - flags request body object schemas that explicitly allow unconstrained additionalProperties (true or an untyped free-form schema)
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

### Filtering and Strict Mode in `cats lint`
`cats lint` supports granular filtering and strictness flags:
- `--linters=NAME,...` (`--linter`): Run only matching linters (full or partial names).
- `--skipLinters=NAME,...` (`--skipLinter`): Skip matching linters globally, or scope skips to specific paths/wildcards using `/path=Linter1;Linter2` or `/api/me/*=PathPluralsLinter`.
- `--skipLintersForExtension=x-ext=val:Linter1,Linter2`: Skip specific linters on endpoints matching an OpenAPI `x-*` extension.
- `-p, --paths=PATH,...` and `--skipPaths=PATH,...`: Include or skip paths (supports wildcards like `/api/*`).
- `-t, --tags=TAG,...` and `--skipTags=TAG,...`: Include or skip OpenAPI tags.
- `--operationIds=ID,...` and `--skipOperationIds=ID,...`: Include or skip specific operationIds.
- `-X, --httpMethods=METHOD,...` and `--skipHttpMethods=METHOD,...`: Include or skip HTTP methods.
- `--skipDeprecatedOperations`: Skip linting deprecated operations.
- `--strict`: Escalates all linter warnings to errors.

Naming conventions can be configured using the following arguments:
- `--headersNaming=<headersNaming>` Naming strategy for HTTP headers. Possible values `SNAKE, KEBAB, PASCAL, CAMEL, HTTP_HEADER, UPPER_UNDERSCORE, LOWER_UNDERSCORE`. Default: `HTTP_HEADER`
- `--jsonObjectsNaming=<jsonObjectsNaming>` Naming strategy for json objects. Default: `PASCAL`
- `--jsonPropertiesNaming=<jsonPropertiesNaming>` Naming strategy for json object properties. Default: `CAMEL`
- `--pathNaming=<pathNaming>` Naming strategy for paths (excluding path variables). Default: `KEBAB`
- `--pathVariablesNaming=<pathVariablesNaming>` Naming strategy for path variables. Default: `CAMEL`
- `--queryParamsNaming=<queryParamsNaming>` Naming strategy for query parameters. Default: `SNAKE`
- `--enumsNaming=<enumsNaming>` Naming strategy for enums. Default: `UPPER_UNDERSCORE`
- `--operationPrefixMapFile=FILE` Custom properties file mapping HTTP verbs to allowed `operationId` verb prefixes.


:::info
`Linter` Fuzzers are disabled by default. You must either use the `cats lint ...` command to run only the linters or
the `--includeLinters` argument to run them along other Fuzzers.
:::

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```
