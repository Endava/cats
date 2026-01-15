---
sidebar_position: 2
description: All CATS arguments
---

# Arguments
You can get the full list of arguments by running `cats -h`. Below is a short description for each:

- `--contract=LOCATION_OF_THE_CONTRACT` supplies the location of the OpenApi or Swagger contract.
- `--server=URL` supplies the URL of the service implementing the contract.
- `--basicauth=USR:PWD` supplies a `username:password` pair, in case the service uses basic auth.
- `--authRefreshInterval=value_in_seconds` Amount of time in seconds after which to get new auth credentials
- `--authRefreshScript=script` Script to get executed after `--authRefreshInterval` in order to get new auth credentials. The script will replace any headers that have `auth_script` as value. If you don't supply a `--authRefreshInterval`, but you supply a script, the script will be used to get the initial auth credentials.
- `--fuzzers=LIST_OF_FUZZERS` supplies a comma separated list of fuzzers. The supplied list of Fuzzers can be partial names, not full Fuzzer names. CATS which check for all Fuzzers containing the supplied strings. If the argument is not supplied, all fuzzers will be run.
- `--log=PACKAGE:LEVEL` can configure custom log level for a given package. You can provide a comma separated list of packages and levels or a level to apply to everything. This is helpful when you want to see full HTTP traffic: `--log=org.apache.http.wire:debug` or suppress CATS logging: `--log=com.endava.cats:warn`
- `--skipLog=LEVELS`  A list of log levels to skip. For example, you can skip only note and info levels, but leave the rest
- `--paths=PATH_LIST` supplies a comma separated list of OpenApi paths to be tested. If no path is supplied, all paths will be considered.
- `--skipPaths=PATH_LIST` a comma separated list of paths to ignore. If no path is supplied, no path will be ignored
- `--fieldsFuzzingStrategy=STRATEGY` specifies which strategy will be used for field fuzzing. Available strategies are `ONEBYONE`, `SIZE` and `POWERSET`. More information on field fuzzing can be found in the sections below.
- `--maxFieldsToRemove=INTEGER` specifies the maximum number of fields to be removed when using the `SIZE` fields fuzzing strategy.
- `--refData=FILE` specifies the file containing static reference data which must be fixed in order to have valid business requests. This is a YAML file. It is explained further in the sections below.
- `--headers=FILE` specifies a file containing headers that will be added when sending payloads to the endpoints. You can use this option to add oauth/JWT tokens for example.
- `--edgeSpacesStrategy=STRATEGY` specifies how to expect the server to behave when sending trailing and prefix spaces within fields. Possible values are `trimAndValidate` and `validateAndTrim`.
- `--sanitizationStrategy=STRATEGY` specifies how to expect the server to behave when sending Unicode Control Chars and Unicode Other Symbols within the fields. Possible values are `sanitizeAndValidate` and `validateAndSanitize`
- `--urlParams param1:value1 param2:value2` A comma separated list of 'name:value' pairs of parameters to be replaced inside the URLs. This is useful when you have static parameters in URLs (like 'version' for example).
- `--functionalFuzzerFile=FILE ` a file used by the `FunctionalFuzzer` that will be used to create user-supplied payloads.
- `--skipFuzzers=LIST_OF_FIZZERs` a comma separated list of fuzzers that will be skipped for **all** paths. You can either provide full `Fuzzer` names (for example: `--skippedFuzzers=VeryLargeStringsFuzzer`) or partial `Fuzzer` names (for example: `--skipFuzzers=VeryLarge`). `CATS` will check if the `Fuzzer` names contains the string you provide in the arguments value.
- `--skipFields=field1,field2#subField1` a comma separated list of fields that will be skipped by replacement Fuzzers like EmptyStringsInFields, NullValuesInFields, etc. **When a field starts with `!` any field that starts with that name will be entirely skipped for fuzzing**
- `--httpMethods=PUT,POST,etc` a comma separated list of HTTP methods that will be used to filter which http methods will be executed for each path within the contract
- `--securityFuzzerFile=FILE` A file used by the `SecurityFuzzer` that will be used to inject special strings in order to exploit possible vulnerabilities
- `--printExecutionStatistics` If supplied (no value needed), prints a summary of execution times for each endpoint and HTTP method. By default this will print a summary for each endpoint: max, min and average. If you want detailed reports you must supply `--printExecutionStatistics=detailed`
- `--timestampReports` If supplied (no value needed), it will output the report still inside the `cats-report` folder, but in a sub-folder with the current timestamp
- `--reportFormat=FORMAT` Specifies the format of the CATS report. Supported formats: `HTML_ONLY`, `HTML_JS` or `JUNIT`. You can use `HTML_ONLY` if you want the report to not contain any Javascript. This is useful in CI environments due to Javascript content security policies. Default is `HTML_JS` which includes some sorting and filtering capabilities.
- `--[no-]useExamples` If `true` then CATS will use ALL examples supplied in the OpenAPI contract. This is equivalent to setting `--usePropertyExamples --useRequestBodyExamples --useResponseBodyExamples --useSchemaExamples`. If `false` CATS will rely only on generated values
- `--[no-]usePropertyExamples` If `true` then CATS will use examples supplied in the OpenAPI contract for properties. If `false` CATS will rely only on generated values
- `--[no-]useRequestBodyExamples` If `true` then CATS will use examples supplied in the OpenAPI contract for request bodies, at media-type level. If `false` CATS will rely only on generated values
- `--[no-]useResponseBodyExamples` If `true` then CATS will use examples supplied in the OpenAPI contract for response bodies, at media-type level. If `false` CATS will rely only on generated values
- `--[no-]useSchemaExamples` If `true` then CATS will use examples supplied in the OpenAPI contract for schemas. If `false` CATS will rely only on generated values
- `--checkFields` If supplied (no value needed), it will only run the `Field` Fuzzers
- `--checkHeaders` If supplied (no value needed), it will only run the `Header` Fuzzers
- `--checkHttp` If supplied (no value needed), it will only run the `HTTP` Fuzzers
- `--includeWhitespaces` If supplied (no value needed), it will include the `Whitespaces` Fuzzers
- `--includeEmojis` If supplied (no value needed), it will include the `Emojis` Fuzzers
- `--includeControlChars` If supplied (no value needed), it will include the `ControlChars` Fuzzers
- `--includeContract` If supplied (no value needed), it will include `ContractInfo` Fuzzers
- `--sslKeystore` Location of the JKS keystore holding certificates used when authenticating calls using one-way or two-way SSL
- `--sslKeystorePwd` The password of the `sslKeystore`
- `--sslKeyPwd` The password of the private key from the `sslKeystore`
- `--proxyHost=INTEGER` The proxy server's host name (if running behind proxy)
- `--proxyPort=INTEGER` The proxy server's port number (if running behind proxy)
- `--maxRequestsPerMinute=INTEGER` Maximum number of requests per minute; this is useful when APIs have rate limiting implemented; default is 10000
- `--connectionTimeout=INTEGER` Time period in seconds which CATS should establish a connection with the server; default is 10 seconds
- `--writeTimeout=INTEGER` Maximum time of inactivity in seconds between two data packets when sending the request to the server; default is 10 seconds
- `--readTimeout=INTEGER` Maximum time of inactivity in seconds between two data packets when waiting for the server's response; default is 10 seconds
- `--dryRun` If provided, it will simulate a run of the service with the supplied configuration. The run won't produce a report, but will show how many tests will be generated and run for each OpenAPI endpoint
- `--ignoreResponseCodes` HTTP_CODES_LIST a comma separated list of HTTP response codes that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR. You can use response code families as `2xx`, `4xx`, etc.
- `--ignoreResponseSize` SIZE_LIST a comma separated list of response sizes that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreResponseWords` COUNT_LIST a comma separated list of words count in the response that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreResponseLines` LINES_COUNT a comma separated list of lines count in the response that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreResponseRegex` a REGEX that will match against the response that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreErrorLeaksCheck`If supplied (no value needed) it won't check if the response body contains sensitive information and will return the test result as SUCCESS instead of ERROR
- `--filterResponseCodes` HTTP_CODES_LIST a comma separated list of HTTP response codes that will be filtered and not included in the final report. You can use response code families as `2xx`, `4xx`, etc.
- `--filterResponseSize` SIZE_LIST a comma separated list of response sizes that will be filtered and not included in the final report
- `--filterResponseWords` COUNT_LIST a comma separated list of words count in the response that will be filtered and not included in the final report
- `--filterResponseLines` LINES_COUNT a comma separated list of lines count in the response that will be filtered and not included in the final report
- `--filterResponseRegex` a REGEX that will match against the response that will be filtered and not included in the final report
- `--tests` TESTS_LIST a comma separated list of executed tests in JSON format from the cats-report folder. If you supply the list without the .json extension CATS will search the test in the cats-report folder
- `--ignoreResponseCodeUndocumentedCheck` If supplied (not value needed) it won't check if the response code received from the service matches the value expected by the fuzzer and will return the test result as SUCCESS instead of WARN
- `--ignoreResponseBodyCheck` If supplied (no value needed) it won't check if the response body received from the service matches the schema supplied inside the contract and will return the test result as SUCCESS instead of WARN
- `--ignoreResponseContentTypeCheck`If supplied (no value needed) it won't check if the response content type matches the one(s) defined in the contract for the corresponding http response code
- `--blackbox` If supplied (no value needed) it will ignore all response codes except for 5XX which will be returned as ERROR. This is similar to `--ignoreResponseCodes="2xx,4xx,501"`
- `--contentType` A custom mime type if the OpenAPI spec uses content type negotiation versioning.
- `--output=PATH` The path where the CATS report will be written. Default is `cats-report` in the current directory
- `--skipReportingForIgnoredCodes` Skip reporting entirely for any of the ignored arguments provided in `--ignoreResponseXXX`
- `--skipReportingForSuccess` Skip reporting entirely for tests cases reported as success. Default: false
- `--skipReportingForWarning` Skip reporting entirely for tests cases reported as warnings. Default: false
- `--largeStringsSize=NUMBER` The size of the strings used by the Fuzzers sending large values like `VeryLargeStringsFuzzer`. Default: `40000`
- `--debug` Sets logging level to all
- `--json` Make specific commands output in `json` format
- `--words=FILE` A custom dictionary that can be supplied. When this is supplied only the `TemplateFuzzer` will be active.
- `--[no-]-checkUpdates`  If true checks if there is a CATS update available and prints the release notes along with the links. Default: true
- `--[no-]-color` If true enables ANSI codes and coloured console output. Default: true
- `--onlyLog=star,note` A list of log levels to include; allows more granular control of the log levels
- `--userAgent=USER_AGENT` The user agent to be set in the User-Agent HTTP header. Default: cats/version
- `--verbosity=DETAILED|SUMMARY`  Sets the verbosity of the console logging. If set to summary CATS will only output a simple progress screen per path. Default: `SUMMARY`
- `--oneOfSelection "field1=value1"`, `--anyOfSelection` A `name=value` list of discriminator names and values that can be use to filter request payloads when objects use oneOf or anyOf definitions which result in multiple payloads for a single endpoint and http method
- `--randomHeadersNumber=NUMBER` The number of random headers that will be sent by the `LargeNumberOfRandomAlphanumericHeadersFuzzer` and `LargeNumberOfRandomHeadersFuzzer`. Default: `10000`
- `--skipFieldTypes=string,integer,etc.` A comma separated list of OpenAPI data types to skip. It only supports standard types: https://swagger.io/docs/specification/data-models/data-types
- `--skipFieldFormats=date,email,etc` A comma separated list of OpenAPI data formats to skip.  It supports formats mentioned in the documentation: https://swagger.io/docs/specification/data-models/data-types
- `--fieldTypes=string,integer,etc.` A comma separated list of OpenAPI data types to include. It only supports standard types: https://swagger.io/docs/specification/data-models/data-types
- `--fieldFormats=date,email,etc.` A comma separated list of OpenAPI data formats to include. It supports formats mentioned in the documentation: https://swagger.io/docs/specification/data-models/data-types
- `--maxResponseTimeInMs` Sets a response time limit in milliseconds. If responses take longer than the provided value, they will get marked as error with reason `Response time exceeds max`. The response time limit check is triggered only if the test case is considered successful i.e. response matches Fuzzer expectations
- `--rfc7396` When set to true it will send Content-Type=application/merge-patch+json for PATCH requests. Default: false`
- `--maskHeaders` A comma separated list of headers to mask to protect sensitive info such as login credentials to be written in report files. Masked headers will be replaced with `$$HeaderName` so that test cases can be replayed using environment variables
- `--tags`  A comma separated list of tags to include. If no tag is supplied, all tags will be considered. To list all available tags run: `cats stats -c api.yml`
- `--skipTags` A comma separated list of tags to ignore. If no tag is supplied, no tag will be ignored. To list all available tags run: `cats stats -c  api.yml`
- `--fuzzersConfig=FILE` A properties file with Fuzzer configuration that changes default behaviour. Configuration keys are prefixed with the fully qualified Fuzzer name  
- `--mutators=FOLDER` A folder containing custom mutators. Only applicable when using the `cats random` sub-command
- `--allowInvalidEnumValues` When set to true the `InvalidValuesInEnumsFieldsFuzzer` will expect a 2XX response code instead of 4XX
- `--selfReferenceDepth=<selfReferenceDepth>` Max depth for objects having cyclic dependencies
- `--limitXxxOfCombinations=<limitXxxOfCombinations>` Max number of anyOf/oneOf combinations
- `--limitFuzzedFields=<numberOfFields>` Max number of fields that will be fuzzed
- `--[no-]useDefaults` If set to true, it will use the default values when generating examples
- `--nameReplace` If set to true, it will simply do a replacement between the targetFields names provided and the fuzz values 
- `--stopAfterErrors=<stopAfterErrors>` Number of errors after which the continuous fuzzing will stop running. Errors are defined as conditions matching the given match arguments. Only available in `cats random` sub-command. 
- `--stopAfterMutations=<stopAfterMutations>` Number of mutations (test cases) after which the continuous fuzzing will stop running. Only available in `cats random` sub-command.
- `--stopAfterTimeInSec=<stopAfterTimeInSec>` Amount of time in seconds for how long the continuous fuzzing will run before stopping. Only available in `cats random` sub-command.
- `--pathsRunOrder=<pathsOrderFile>` A file with the order in which the paths will be executed. The paths are on each line. The order from file will drive the execution order
- `--errorLeaksKeywords=<errorLeaksKeywords>` A properties file with error leaks keywords that will be used when processing responses to detect potential error leaks. If one of these keyword is found, the test case will be marked as error
- `-P name=value` A list of `name=value` pairs that will be used to replace url params for all paths
- `-H name=value` A list of `name=value` pairs that will be used to replace headers for all paths
- `-Q name=value` A list of `name=value` pairs that will be used to replace query params for all paths
- `-R name=value` A list of `name=value` pairs that will be used as reference data for all paths
- `--operationIds=<operationIds>` A comma separated list of operationIds to include. If no operationId is supplied, all operationIds will be considered.
- `--skipOperationIds=<skipOperationIds>` A comma separated list of operationIds to ignore. If no operationId is supplied, no operationId will be ignored.
- `--skipFuzzersForExtension` Skip specific fuzzers for endpoints with certain OpenAPI extension values. Format: x-extension-name=value:Fuzzer1,Fuzzer2.
- `--resolveXxxOfCombinationForResponses` Resolve anyOf/oneOf combinations for responses. Default: false
- `--http2PriorKnowledge` If set to `true`, it will force a http2 connection, without fallback to HTTP 1.X
- `--includeAllInjectionPayloads` Include all injection payloads for security fuzzers (SQL, XSS, Command, NoSQL injection). By default, only a curated top 10 payloads are used per injection type to reduce execution time.
- `--seed=<value>` a seed used for deterministic generation of the random values

:::tip
When you want to skip fuzzing entirely for a specific JSON object or specific fields you must prefix the field name from the `--skipFields` argument with `!`.
The following `--skipFields="!address"` will skip fuzzing for all sub-fields of the `address` object. If you also want CATS to not sent the `address` 
object at all to the service (sometimes some object might not make sense in conjunction with other objects) you must also use the `cats_remove_field`
within the reference data file.

```yaml
all:
  address: cats_remove_field
```

:::

Next arguments are active only when supplying a custom dictionary via `--words`:
- `--matchResponseCodes=<matchResponseCodes>[,<matchResponseCodes>...]` A comma separated list of HTTP response codes that will be matched as error. All other response codes will be ignored from the final report. If provided, all Contract Fuzzers will be skipped
- `--matchResponseLines=<matchResponseLines>[,<matchResponseLines>...]` A comma separated list of number of line counts in the response that will be matched as error. All other response line counts will be ignored from the final report. If provided, all Contract Fuzzers will be skipped
- `--matchResponseRegex=<matchResponseRegex>` A regex that will match against the response that will be matched as error. All other response body matches will be ignored from the final report. If provided, all Contract Fuzzers will be skipped
- `--matchResponseSize=<matchResponseSizes>[,<matchResponseSizes>...]` A comma separated list of response sizes that will be matched as error. All other response sizes will be ignored from the final report. If provided, all Contract Fuzzers will be skipped
- `--matchResponseWords=<matchResponseWords>[,<matchResponseWords>...]` A comma separated list of word counts in the response that will be matched as error. All other response word counts will be ignored from the final report. If provided, all Contract Fuzzers will be skipped

```bash
cats --contract=my.yml --server=https://locathost:8080 --checkHeaders
```

This will run CATS against `http://localhost:8080` using `my.yml` as an API spec and will only run the HTTP headers Fuzzers.
