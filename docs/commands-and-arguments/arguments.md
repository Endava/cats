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
- `--authRefreshScript=script` Script to get executed after `--authRefreshInterval` in order to get new auth credentials. The script will replace any headers that have aut_script as value. If you don't supply a --authRefreshInterval, but you supply a script, the script will be used to get the initial auth credentials.
- `--fuzzers=LIST_OF_FUZZERS` supplies a comma separated list of fuzzers. The supplied list of Fuzzers can be partial names, not full Fuzzer names. CATS which check for all Fuzzers containing the supplied strings. If the argument is not supplied, all fuzzers will be run.
- `--log=PACKAGE:LEVEL` can configure custom log level for a given package. You can provide a comma separated list of packages and levels or a level to apply to everything. This is helpful when you want to see full HTTP traffic: `--log=org.apache.http.wire:debug` or suppress CATS logging: `--log=com.endava.cats:warn`
- `--skipLog=LEVELS`  A list of log levels to skip. For example you can skip only note and info levels, but leave the rest
- `--paths=PATH_LIST` supplies a comma separated list of OpenApi paths to be tested. If no path is supplied, all paths will be considered.
- `--skipPaths=PATH_LIST` a comma separated list of paths to ignore. If no path is supplied, no path will be ignored
- `--fieldsFuzzingStrategy=STRATEGY` specifies which strategy will be used for field fuzzing. Available strategies are `ONEBYONE`, `SIZE` and `POWERSET`. More information on field fuzzing can be found in the sections below.
- `--maxFieldsToRemove=NUMBER` specifies the maximum number of fields to be removed when using the `SIZE` fields fuzzing strategy.
- `--refData=FILE` specifies the file containing static reference data which must be fixed in order to have valid business requests. This is a YAML file. It is explained further in the sections below.
- `--headers=FILE` specifies a file containing headers that will be added when sending payloads to the endpoints. You can use this option to add oauth/JWT tokens for example.
- `--edgeSpacesStrategy=STRATEGY` specifies how to expect the server to behave when sending trailing and prefix spaces within fields. Possible values are `trimAndValidate` and `validateAndTrim`.
- `--sanitizationStrategy=STRATEGY` specifies how to expect the server to behave when sending Unicode Control Chars and Unicode Other Symbols within the fields. Possible values are `sanitizeAndValidate` and `validateAndSanitize`
- `--urlParams` A comma separated list of 'name:value' pairs of parameters to be replaced inside the URLs. This is useful when you have static parameters in URLs (like 'version' for example).
- `--functionalFuzzerFile` a file used by the `FunctionalFuzzer` that will be used to create user-supplied payloads.
- `--skipFuzzers=LIST_OF_FIZZERs` a comma separated list of fuzzers that will be skipped for **all** paths. You can either provide full `Fuzzer` names (for example: `--skippedFuzzers=VeryLargeStringsFuzzer`) or partial `Fuzzer` names (for example: `--skipFuzzers=VeryLarge`). `CATS` will check if the `Fuzzer` names contains the string you provide in the arguments value.
- `--skipFields=field1,field2#subField1` a comma separated list of fields that will be skipped by replacement Fuzzers like EmptyStringsInFields, NullValuesInFields, etc.
- `--httpMethods=PUT,POST,etc` a comma separated list of HTTP methods that will be used to filter which http methods will be executed for each path within the contract
- `--securityFuzzerFile` A file used by the `SecurityFuzzer` that will be used to inject special strings in order to exploit possible vulnerabilities
- `--printExecutionStatistics` If supplied (no value needed), prints a summary of execution times for each endpoint and HTTP method. By default this will print a summary for each endpoint: max, min and average. If you want detailed reports you must supply `--printExecutionStatistics=detailed`
- `--timestampReports` If supplied (no value needed), it will output the report still inside the `cats-report` folder, but in a sub-folder with the current timestamp
- `--reportFormat=FORMAT` Specifies the format of the CATS report. Supported formats: `HTML_ONLY`, `HTML_JS` or `JUNIT`. You can use `HTML_ONLY` if you want the report to not contain any Javascript. This is useful in CI environments due to Javascript content security policies. Default is `HTML_JS` which includes some sorting and filtering capabilities.
- `--useExamples` If `true` (default value when not supplied) then CATS will use examples supplied in the OpenAPI contact. If `false` CATS will rely only on generated values
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
- `--proxyHost` The proxy server's host name (if running behind proxy)
- `--proxyPort` The proxy server's port number (if running behind proxy)
- `--maxRequestsPerMinute` Maximum number of requests per minute; this is useful when APIs have rate limiting implemented; default is 10000
- `--connectionTimeout` Time period in seconds which CATS should establish a connection with the server; default is 10 seconds
- `--writeTimeout` Maximum time of inactivity in seconds between two data packets when sending the request to the server; default is 10 seconds
- `--readTimeout` Maximum time of inactivity in seconds between two data packets when waiting for the server's response; default is 10 seconds
- `--dryRun` If provided, it will simulate a run of the service with the supplied configuration. The run won't produce a report, but will show how many tests will be generated and run for each OpenAPI endpoint
- `--ignoreResponseCodes` HTTP_CODES_LIST a comma separated list of HTTP response codes that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR. You can use response code families as `2xx`, `4xx`, etc.
- `--ignoreResponseSize` SIZE_LIST a comma separated list of response sizes that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreResponseWords` COUNT_LIST a comma separated list of words count in the response that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreResponseLines` LINES_COUNT a comma separated list of lines count in the response that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--ignoreResponseRegex` a REGEX that will match against the response that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR
- `--tests` TESTS_LIST a comma separated list of executed tests in JSON format from the cats-report folder. If you supply the list without the .json extension CATS will search the test in the cats-report folder
- `--ignoreResponseCodeUndocumentedCheck` If supplied (not value needed) it won't check if the response code received from the service matches the value expected by the fuzzer and will return the test result as SUCCESS instead of WARN
- `--ignoreResponseBodyCheck` If supplied (not value needed) it won't check if the response body received from the service matches the schema supplied inside the contract and will return the test result as SUCCESS instead of WARN
- `--blackbox` If supplied (no value needed) it will ignore all response codes except for 5XX which will be returned as ERROR. This is similar to `--ignoreResponseCodes="2xx,4xx"`
- `--contentType` A custom mime type if the OpenAPI spec uses content type negotiation versioning.
- `--output` The path where the CATS report will be written. Default is `cats-report` in the current directory
- `--skipReportingForIgnoredCodes` Skip reporting entirely for any of the ignored arguments provided in `--ignoreResponseXXX`

```bash
cats --contract=my.yml --server=https://locathost:8080 --checkHeaders
```

This will run CATS against `http://localhost:8080` using `my.yml` as an API spec and will only run the HTTP headers Fuzzers.
