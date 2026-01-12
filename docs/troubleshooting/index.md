# Troubleshooting

## CATS seems to be stuck

These are some of the common reasons why CATS might appear to be stuck:

- **Network issues**: CATS might be waiting for a response from the server. Check the network connection and the server status.
- **Server issues**: The server might be slow or unresponsive. Check the server logs and the server status.
- **Many fields inside the request(s)**: If the request has many fields, CATS might take longer to generate the tests, as many fuzzers are running for each field. You can limit the number of fields to be fuzzed using `--limitFuzzedFields` argument.
- **Usage of anyOf/oneOf in request schemas**: If the request schema contains `anyOf` or `oneOf`, CATS will generate tests for all possible combinations. This can lead to a large number of tests being generated. 
You can limit the number of combinations by using the `--limitXxxCombinations` argument. For example, `--limitXxxOfCombinations=10` will limit the number of combinations for all `oneOf/anyOf` to 10.
- **Self-reference in the request schema**: If the request schema contains self-references, when CATS generates the request sample it might enter an infinite loop. You can limit the depth of the schema by using the `--selfReferenceDepth` argument. For example, `--selfReferenceDepth=3` will limit the self-reference depth of the schema to 3.

When running in default mode, CATS will print progress as it processes paths and displays the current fuzzer that is being run. If you see that the same fuzzer is being run for a long time, it might be stuck. You can stop CATS by pressing `Ctrl+C` and run again with `--verbosity=DETAILED --debug` to see more details.

## CATS is not generating meaningful values in the request

CATS generates values based on the request schema using examples or request field names and/or formats. If the request schema is not well-defined, CATS might generate values that are not meaningful. 
You can set examples for each property or provide specific formats in the schema to guide CATS in generating meaningful values. You can list all formats supported by CATS using `cats list --formats`.

## CATS is generating too many tests

CATS generates tests based on the request schema and the fuzzers that are enabled. If the request schema is complex or has many fields, CATS might generate a large number of tests. 
You can check the following pages for different strategies: [Slicing Strategies](https://endava.github.io/cats/docs/getting-started/slicing-strategies) and [Filtering Options](https://endava.github.io/cats/docs/getting-started/filtering-reports).

## CATS stops, saying it cannot generate values for a field

CATS generates values based on the request schema using examples or request field names and/or formats. If the request schema is not well-defined, CATS might not be able to generate values for some fields.
There might be cases when the `pattern` for a specific property is too complicated and all internal generators are not able to create a value that it's matching that pattern.
The recommendation in this case is to either provide an example for that property, provide a specific formats in the schema to guide CATS in generating meaningful values or to create a simpler version of the `pattern`. 

## CATS report takes too long to open

This is usually when CATS generates a large number of tests and the report is too big. You can check the following pages for different strategies: [Slicing Strategies](https://endava.github.io/cats/docs/getting-started/slicing-strategies) and [Filtering Options](https://endava.github.io/cats/docs/getting-started/filtering-reports).

## CATS generates same number of tests in 2 consecutive runs with same configuration

Unlike typical fuzzers that rely only on randomness, CATS generates tests based on the request schema and the fuzzers that are enabled. If the request schema and the configuration are the same, CATS will generate the same tests.
CATS uses a deterministic approach to generate tests based on a set of fuzzers which run in a specific order and specific logic. Randomness is introduced only in the values generated for the fields.
If you want randomness also in the tests generated, you must use the `cats random` sub-command. Check [Continuous Fuzzing](https://endava.github.io/cats/docs/getting-started/running-cats#continuous-fuzzing-mode) for more details.

## CATS doesn't generate any tests

Assuming the OpenAPI file contains at least one valid path with at least one operation, there might be different reason why CATS is not generating any test:
- **Fuzzers are disabled**: By default, CATS runs with all fuzzers enabled. If you use the `--fuzzer` argument, make sure you supply valid fuzzer names
- **Filters are too restrictive**: Either all test cases are filtered out by the `--ignoreXXX` arguments or not test case is matching `--matchXXX` arguments
- **Non-existent paths**: If you supply the `--path` argument, make sure the path exists in the OpenAPI file

## CATS generates tests that are not valid

If you think some of the test cases generated are not valid in your scenario you can:
- **Filter out the invalid test cases**: You can use the `--ignoreXXX` or `--filterXXX` arguments to filter out the test cases that are not valid
- **Filter out specific fuzzers**: You can use the `--skipFuzzer` argument to filter out specific fuzzers that are generating invalid test cases
- **Filter out specific paths**: You can use the `--skpiPath` argument to filter out specific paths that are generating invalid test cases
- **Filter out specific http methods**: You can use the `--skipHttpMethod` argument to filter out specific http methods that are generating invalid test cases
- **Filter out specific request fields**: You can use the `--skipField` argument to filter out specific request fields that are generating invalid test cases
- **Filter out specific OpenAPI formats**: You can use the `--skipFormat` argument to filter out specific OpenAPI formats that are generating invalid test cases
- **Filter out specific OpenAPI field types**: You can use the `--skipFieldType` argument to filter out specific OpenAPI field types that are generating invalid test cases
- **Filter out specific request headers**: You can use the `--skipHeader` argument to filter out specific request headers that are generating invalid test cases

If you think there might be a bug in CATS, please open an issue on the [GitHub repository](https://github.com/Endava/cats/issues/new/choose).

## Change expected response code for specific fuzzers

You can check expected response codes for each fuzzer using [Customize the Default Response Code](https://endava.github.io/cats/docs/advanced-topics/fuzzers-config/).

## CATS generates requests that are not valid

In very rare cases CATS might generate requests that are not valid. 
This can happen if the OpenAPI file is not well-defined or if the request schema uses complex combinations of `anyOf`, `oneOf` and/or `allOf`. 
If you think there might be a bug in CATS, please open an issue on the [GitHub repository](https://github.com/Endava/cats/issues/new/choose).
