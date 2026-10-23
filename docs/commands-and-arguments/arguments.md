---
sidebar_position: 2
description: All CATS arguments
---

# Arguments

The complete, version-specific list is always available with `cats --help`.
The options below are grouped by purpose and apply to the OpenAPI fuzzing
command unless noted otherwise. Most long options accept either `--name=value`
or `--name value`.

## Contract and service

- `-c, --contract=FILE` supplies the OpenAPI contract.
- `-s, --server=URL` supplies the base URL of the service.
- `--contentType=TYPE` overrides the MIME type used for content-type negotiation versioning.
- `--callTimeout=SECONDS` sets the maximum total time for an HTTP call (`0` disables the limit). Default: `20`.
- `--connectionTimeout=SECONDS` sets the connection timeout. Default: `10`.
- `--readTimeout=SECONDS` sets the maximum response read inactivity. Default: `10`.
- `--writeTimeout=SECONDS` sets the maximum request write inactivity. Default: `10`.
- `--maxResponseBytes=BYTES` sets the maximum number of decompressed response-body bytes to capture per HTTP call (`0` disables the limit). Default: `10485760`.
- `--maxRequestsPerMinute=NUMBER` limits request throughput. Default: `10000`.
- `--userAgent=VALUE` sets the `User-Agent` header. Default: `cats/VERSION`.
- `--configFile=FILE` loads command configuration from a file.

## Authentication and request data

- `--basicAuth=USER:PASSWORD` supplies HTTP basic authentication. `--basicauth` is an alias.
- `--authRefreshInterval=SECONDS` controls how often CATS refreshes credentials.
- `--authRefreshScript=SCRIPT` runs a script to obtain refreshed credentials. Headers whose value is `auth_script` are replaced with the script output.
- `--ari` and `--ars` are short aliases for the authentication refresh options.
- `--sslKeystore=FILE`, `--sslKeystorePwd=PASSWORD`, and `--sslKeyPwd=PASSWORD` configure mutual TLS (client certificate and private key).
- `--insecure` disables TLS certificate and hostname verification. Use only for trusted test environments.
- `--envFile=FILE` loads fallback environment variables from a dotenv file instead of the auto-detected `./.env`.
- `--noEnvFile` or `--no-env-file` disables automatic loading of `./.env`.
- `--proxyHost=HOST` and `--proxyPort=PORT` configure the proxy server.
- `--headers=FILE` loads per-path headers from YAML. `-H name=value` adds a header globally.
- `--queryParams=FILE` loads per-path query parameters from YAML. `-Q name=value` adds a query parameter globally.
- `--refData=FILE` loads fixed values required for valid business requests. `-R name=value` adds reference data globally.
- `--correlations=FILE` or `--correlate=FILE` loads cross-field correlation rules (`when`/`then` `set` or `omit`) applied to request payloads across live HTTP commands.
- `--urlParams=name:value,...` replaces path parameters globally. `-P name=value` is the equivalent global parameter form.
- `--functionalFuzzerFile=FILE` supplies the functional test or sequential functional flow file used by `cats run`.
- `--securityFuzzerFile=FILE` supplies the configuration used by the `SecurityFuzzer`.
- `--createRefData` asks the `FunctionalFuzzer` to create a reference-data file from the paths and output variables in the functional file.
- `--wfcAuth=FILE` loads a Web Fuzzing Commons authentication YAML/JSON file. CATS resolves the selected entry and applies its headers, cookies, or query parameters.
- `--wfcAuthName=NAME` selects an entry from `--wfcAuth`; if omitted, CATS starts with the first entry and switches to subsequent entries on HTTP `401` or `403`.

See [Authentication](../getting-started/authentication) for the built-in
authentication options and [WFC authentication](../getting-started/wfc-auth)
for Web Fuzzing Commons configuration.

## Fuzzer selection and scope

- `-f, --fuzzers=FUZZER,...` runs only matching fuzzers. Full or partial names are accepted. Use `cats list --fuzzers` to see the current names.
- `--skipFuzzers=FUZZER,...` ignores matching fuzzers.
- `--skipFuzzersForExtension=EXTENSION=VALUE:Fuzzer1,Fuzzer2` skips fuzzers for endpoints having a matching OpenAPI extension. `--skipFuzzerForExtension` is an alias.
- `--mode=ALL|NEGATIVE|POSITIVE` selects all scenarios, only negative scenarios, or only positive/happy-path scenarios. Default: `ALL`.
- `--profile=security|quick|compliance|ci|coverage|crash|type-coercion|health-check|full` selects a built-in fuzzer profile. Default: `full`.
- `--profileFile=FILE` loads custom fuzzer profiles from YAML.
- `--healthCheck` runs the minimal `health-check` profile before full fuzzing. Default: `false`.
- `--checkFields` runs only Field Fuzzers.
- `--checkHeaders` runs only Header Fuzzers.
- `--checkHttp` runs only HTTP Fuzzers.
- `--includeLinters` or `--includeContract` includes Contract/Linter Fuzzers.
- `--checkContract` is an alias for including Contract/Linter Fuzzers.
- `--includeWhitespaces`, `--includeEmojis`, and `--includeControlChars` enable the corresponding opt-in fuzzers.
- `-p, --paths=PATH,...` limits execution to selected paths. `--path` is an alias.
- `--skipPaths=PATH,...` excludes paths.
- `--operationIds=ID,...` limits execution to selected operation IDs. `--operationId` is an alias.
- `--skipOperationIds=ID,...` excludes operation IDs.
- `--tags=TAG,...` limits execution to selected tags. `--tag` is an alias.
- `--skipTags=TAG,...` excludes tags.
- `-X, --httpMethods=METHOD,...` limits execution to selected HTTP methods (`POST, PUT, GET, DELETE, PATCH, HEAD` by default; `TRACE` is also supported when explicitly included). `--httpMethod` is an alias.
- `--skipHttpMethods=METHOD,...` excludes HTTP methods.
- `--skipDeprecatedOperations` excludes deprecated API operations. Default: `false`.
- `--skipFields=FIELD,...` excludes fields from replacement fuzzers. Prefix a field with `!` to skip that field and all descendants.
- `--skipHeaders=HEADER,...` excludes headers from all fuzzers.
- `--fieldTypes=TYPE,...` and `--fieldFormats=FORMAT,...` include only matching OpenAPI field types or formats.
- `--skipFieldTypes=TYPE,...` and `--skipFieldFormats=FORMAT,...` exclude matching OpenAPI field types or formats.

## Processing

- `--fieldsFuzzingStrategy=ONEBYONE|SIZE|POWERSET` selects the field-removal strategy. Default: `ONEBYONE`.
- `--maxFieldsToRemove=NUMBER` limits fields removed by the `SIZE` strategy.
- `--limitFuzzedFields=NUMBER` limits request fields selected for fuzzing. `0` means all fields.
- `--largeStringsSize=NUMBER` controls the size of generated large strings. Default: `40000`.
- `--randomHeadersNumber=NUMBER` controls the number of generated random headers. Default: `10000`.
- `--selfReferenceDepth=NUMBER` limits generated depth for cyclic request objects. Default: `2`.
- `--limitXxxOfCombinations=NUMBER` limits generated `oneOf`/`anyOf` combinations. Default: `20`.
- `--resolveXxxOfCombinationForResponses` also resolves `oneOf`/`anyOf` combinations in responses.
- `--discriminatorCasing=STYLE` chooses the casing for discriminator values when the contract has no explicit enum or mapping. Supported styles include `PascalCase`, `camelCase`, `UPPER_SNAKE_CASE`, `lower_snake_case`, `kebab-case`, and `lowercase`.
- `--oneOfSelection=name=value,...` or `--anyOfSelection=name=value,...` selects discriminator values when multiple request payloads are possible.
- `--[no-]useDefaults` controls whether schema defaults are used. Default: `true`.
- `--[no-]useExamples` enables the contract's request, schema, property, and response examples together.
- `--usePropertyExamples`, `--useRequestBodyExamples`, `--useResponseBodyExamples`, and `--useSchemaExamples` control individual example sources; the property and response options are negatable with `--no-...`.
- `--[no-]cachePayloads` reuses generated payload examples for the same schema. Default: `true`.
- `--[no-]reuseSuccessfulResources` controls whether successful request baselines and correlated resource values from `2XX` responses are reused in later requests. Default: `true` starting with CATS 14.1.0. See [Runtime resource reuse](../advanced-topics/runtime-resource-reuse).
- `--preseedCollectionGets` runs selected unparameterized collection `GET` operations without required parameters before normal path fuzzing to capture existing resource identifiers. Default: `false`.
- `--maxPreseedCollectionGets=NUMBER` limits the number of collection `GET` requests sent by `--preseedCollectionGets` (`0` sends none). Default: `20`.
- `--strictTypes` expects `2XX` for type-coercion scenarios; `--no-strictTypes` permits the normal `4XX` expectation. Default: enabled.
- `--allowInvalidEnumValues` expects `2XX` when invalid enum values are sent. Default: `false`.
- `--edgeSpacesStrategy=VALIDATE_AND_TRIM|TRIM_AND_VALIDATE` controls expectations for leading or trailing spaces.
- `--sanitizationStrategy=SANITIZE_AND_VALIDATE|VALIDATE_AND_SANITIZE` controls expectations for Unicode control characters and other symbols.
- `--http2PriorKnowledge` forces HTTP/2 without falling back to HTTP/1.x.
- `--rfc7396` uses `application/merge-patch+json` for PATCH requests.
- `--checkAllowHeader` checks that responses to HTTP-method fuzzers contain a correct `Allow` header.
- `--pathsRunOrder=FILE` executes paths in the order listed in the file.
- `--fuzzersConfig=FILE` loads per-fuzzer response-code configuration from a properties file.
- `--errorLeaksKeywords=FILE` supplies keywords used to detect possible error leaks.
- `--seed=NUMBER` makes random value generation deterministic. Default: `0`.
- `--dryRun` prints the planned test count and fuzzer selection without invoking the service.
- `--blackbox` ignores response mismatches except for `5XX` errors.
- `--includeAllInjectionPayloads` uses the complete SQL, XSS, command, and NoSQL injection payload sets instead of the curated top ten per type.

## Response handling and reporting

- `--ignoreResponseCodes=CODE,...`, `--ignoreResponseSize=SIZE,...`, `--ignoreResponseWords=COUNT,...`, and `--ignoreResponseLines=COUNT,...` treat matching responses as success.
- `--ignoreResponseRegex=REGEX` treats matching response bodies as success.
- `--ignoreResponseContentTypeCheck`, `--ignoreResponseBodyCheck`, `--ignoreErrorLeaksCheck`, and `--ignoreResponseCodeUndocumentedCheck` disable the corresponding checks.
- `--filterResponseCodes=CODE,...`, `--filterResponseSize=SIZE,...`, `--filterResponseWords=COUNT,...`, `--filterResponseLines=COUNT,...`, and `--filterResponseRegex=REGEX` omit matching results from the report.
- `--skipReportingForIgnoredCodes` omits results matching ignored response conditions. `--skipReportingForSuccess` and `--skipReportingForWarning` omit successful or warning results. Their short aliases are `--sri`, `--srs`, and `--srw`.
- Short aliases are available for common response options: `-i`/`--ic` for ignored codes, `-k`/`--sri` for skipped ignored results, `-b` for blackbox, `--fc`, `--fl`, `--fr`, `--fs`, and `--fw` for filters, and `--ib`, `--ie`, `--il`, `--ir`, `--is`, `--it`, `--iu`, and `--iw` for ignore checks. Match options also have `--mc`, `--mi`, `--ml`, `--mr`, `--ms`, and `--mw` aliases.
- `--maxResponseTimeInMs=MILLISECONDS` reports successful responses exceeding the limit as errors.
- `-o, --output=DIR` selects the report directory. Default: `cats-report`.
- `--reportFormat=HTML_JS|HTML_ONLY|HTML_JS_CLUSTERS|JUNIT|WFC_REPORT,...` selects one or more comma-separated report formats to generate in the same run (for example `--reportFormat HTML_JS,WFC_REPORT`). Default: `HTML_JS`.
- `--timestampReports` places each run in a timestamped subdirectory.
- `--printExecutionStatistics` prints endpoint/method timing summaries.
- `--printDetailedExecutionStatistics` prints per-request timing details.
- `--printProgress` prints URLs matching the configured filters.
- `--tui` runs OpenAPI fuzzing in the interactive terminal interface.
- `--tuiMaxResults=NUMBER` controls how many recent test details the TUI retains. Default: `10000`.
- `--verbosity=SUMMARY|DETAILED` controls console logging detail. Default: `SUMMARY`.
- `--[no-]checkUpdate` controls update checks. Default: `true`.
- `--[no-]color` enables or disables coloured output. Default: `true`.
- `-j, --json` selects JSON output for commands that support it.
- `-D, --debug` enables all CATS log levels.
- `-l, --log=PACKAGE:LEVEL,...` sets custom package log levels.
- `-O, --onlyLog=LEVEL,...` includes only selected log levels.
- `-g, --skipLog=LEVEL,...` excludes selected log levels.
- `--maskHeaders=HEADER,...` masks additional headers in console output and report files (common sensitive headers and query parameters are masked automatically by default).
- `--showSecrets` disables automatic masking of sensitive headers and query parameters.
- `--nameReplace` or `--simpleReplace` applies dictionary values by replacing the selected target field names directly.

## Quality gates and stop conditions

- `--failOn=error,warn` makes CATS exit with code `1` when any selected result type is reported. Default: `error`.
- `--qualityGate="errors<5,warns<20"` sets numeric thresholds. Supported metrics are `errors`, `warns`, and `warnings` (non-negative integer thresholds); quality-gate thresholds take precedence over `--failOn`.
- `--baseline=FILE_OR_DIR` compares the current run apples-to-apples against a baseline `cats-report` directory or `cats-summary-report.json` file across shared `(path, httpMethod, fuzzer)` scopes, failing only when new error regressions are introduced.
- `--failOnNewWarnings` when used with `--baseline`, also fails the build if new warnings are introduced compared to the baseline.
- `--stopAfterErrors=NUMBER` stops after the specified number of error results. `--se` is a short alias.
- `--stopAfterTests=NUMBER` stops after the specified number of test cases. `--stopAfterMutations` and `--sm` are aliases.
- `--stopAfterTimeInSec=SECONDS` stops after the specified elapsed time. `--st` is a short alias.
- `--fillTimeBudget` (or `--withRandom`) continues fuzzing with `RandomFuzzer` after the deterministic fuzzers finish until the `--stopAfterTimeInSec` time budget expires.

Stop conditions apply to normal OpenAPI fuzzing as well as continuous and
template fuzzing. When an execution limit is reached, the report records the
stop condition and the run summary still reflects the tests already executed.
When `--fillTimeBudget` (`--withRandom`) is used with `--stopAfterTimeInSec`
(or `RandomFuzzer` is explicitly included in `--fuzzers`), CATS uses any
remaining time after the deterministic fuzzers finish to run continuous
`RandomFuzzer` rounds.

## Custom dictionary matching

These options are active when a custom dictionary is supplied with `--words`:

- `--words=FILE` loads a custom dictionary.
- `--matchResponseCodes=CODE,...`, `--matchResponseSize=SIZE,...`, `--matchResponseWords=COUNT,...`, and `--matchResponseLines=COUNT,...` mark matching responses as errors.
- `--matchResponseRegex=REGEX` marks matching response bodies as errors.
- `--matchInput` marks a test as an error when the response reflects the fuzzed input.

`--cmt` is the short alias for `--customMutatorTypes`. `--version` prints the
CATS version and exits.

For the short option aliases and command-specific options, run:

```bash
cats --help
cats analyze --help
cats init --help
cats smoke --help
cats compare --help
cats list --help
cats replay --help
cats run --help
cats template --help
cats random --help
```

## Command-specific options

- `cats analyze` (alias `blueprint`) requires `-c, --contract=FILE` and supports `--mermaid` and `-j, --json`.
- `cats init` requires `-c, --contract=FILE` and supports `-o, --output=DIR`, `--force`, and `-j, --json`.
- `cats smoke` (alias `probe`) supports the standard contract, server, authentication, files, filter, and reporting options plus `--includeDelete` and `-j, --json`.
- `cats compare` (alias `diff`) requires `-b, --baseline=FILE_OR_DIR` and `-t, --target=FILE_OR_DIR`, and supports `--failOnNewErrors` (default: `true`), `--failOnNewWarnings`, and `-j, --json`.
- `cats list` supports `--fuzzers`, `--linters`, `--profiles`, `--formats`, `--mutators`, `--customMutatorTypes`, `--fieldsFuzzerStrategies`, and `--paths` (with `--executionOrder` or `--dependencyOrder` to show producer-to-consumer execution order). Use `--json` for machine-readable output.
- `cats replay TEST` supports `--errors`, `--warnings`, `--reportFolder=DIR`, `--output=DIR`, `--server=URL`, `--callTimeout=SECONDS`, `--maxResponseBytes=BYTES`, `--curl` (prints equivalent `curl` commands without sending HTTP calls), and `--verbose`.
- `cats run FILE` supports `--correlations=FILE` (`--correlate=FILE`), `--headers=FILE`, `-H`, `--queryParams=FILE`, `--refData=FILE`, `--createRefData`, `--contentType=TYPE`, `--oneOfSelection`, `--anyOfSelection`, `--discriminatorCasing`, and `--seed`.
- `cats template URL` supports `--data=JSON_OR_FILE`, `--targetFields=FIELD,...`, `--httpMethod=METHOD`, `--headers`, and `--random`.
- `cats lint` supports `--skipLinters=NAME,...`, `--strict`, `--enumsNaming`, `--headersNaming`, `--jsonObjectsNaming`, `--jsonPropertiesNaming`, `--operationPrefixMapFile`, `--pathNaming`, `--pathVariablesNaming`, and `--queryParamsNaming`.
- `cats stats` supports `--detailed` and `--skip=SECTION,...`.
- `cats validate` supports `--detailed`.
- `cats generate` supports `--path=PATH`, `--httpMethod=METHOD`, `--limit=NUMBER`, `--selfReferenceDepth=NUMBER`, `--pretty`, and `--[no-]useExamples`.
- `cats explain` requires `--type=fuzzer|linter|profile|mutator|response_code|error_reason` and a positional value to explain.
- `cats generate-completion` prints the shell completion script. Enable it in the current shell with `source <(cats generate-completion)`.
