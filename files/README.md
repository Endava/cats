# Examples of files used by CATS

This folder contains examples of various files used by CATS. There are also examples on how each file can be used,
as well as links to the CATS documentation for additional details.

## Headers File

CATS support both argument level headers using `-H` as well as path-level headers using the `--headers <FILE>` argument.

The [headers.yml](./headers.yml) is an example on how to supply headers to all requests using the `all:` entry,
as well as path-specific headers using the `/pets:` entry.

**Please note that headers must be sub-elements of `all` or specific paths.**

## Reference Data File

Supplying reference data is typically needed for more in-depth fuzzing. You can supply such a file using
the `--refData <FILE>` argument.

The [referenceFields.yml](./referenceFields.yml) is an example on how you can supply reference data for all requests
using the `all:` entry, as well as to individual paths using the `/pets/{id}:` entry.

**Please note that reference data entries must be sub-elements of `all` or specific paths.**

## Security Fuzzer File

The [Security Fuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/security-fuzzer) allows fuzzing using
custom dictionaries.

The [securityFuzzer](./securityFuzzer.yml) is such an example that can be run using
the `cats run ... securityFuzzer.yml` sub-command.

Please note that the structure of the file expects entries per path and each path to have a series of tests.
Paths must match exactly with the OpenAPI specs. Each test expects certain keywords which are defined in the
documentation.

The current example will iterate through all the values form the [xss.txt](./xss.txt) file and subsequently replace
`id` and `name` with those values and expect to receive a `200` response code from the service.

## Functional Fuzzer File

The [Functional Fuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/functional-fuzzer) leverages the
payload generation capabilities of CATS and allows to write simple functional tests with minimum syntax, overriding just
the fields that require specific values.

The [functionalFuzzer.yml](./functionalFuzzer.yml) is such an example that can be run using
the `cats run ... functionalFuzzer.yml` sub-command.

The structure is similar to the security fuzzer file with entries per path and each pat having a series of tests.
Please check the documentation for more details about syntax, keywords, test correlation and so on.

## Fuzz Configuration

By default, each Fuzzer has an expected response code based on its specific test case. You can override the expected
response code by providing a properties file through the `--fuzzersConfig` argument.

The [fuzzConfig.properties](./fuzzConfig.properties) shows how you can override the default response code
for the `DummyAcceptHeaders` fuzzer to expect `403` instead of the default `406`.

More documentation here: [--fuzzConfig](https://endava.github.io/cats/docs/advanced-topics/fuzzers-config).

## Custom Mutators

When
doing [continuous fuzzing](https://endava.github.io/cats/docs/getting-started/running-cats/#continuous-fuzzing-mode)
you can supply custom mutators using the `cats random ... --mutators <FOLDER>`.

The [mutators](./mutators) folder contains some sample mutators that can be used for continuous fuzzing.

[first.yml](./mutators/first.yml) and [second.yml](./mutators/second.yml) provides the mutation values
through an array of values within the yaml file, while the [third.yml](./mutators/third.yml) will load
the values from the [nosql.txt](./nosql.txt) file.

## Error Leaks Keywords

CATS automatically checks for error leaks in the responses. It has an internal list of keywords for the most popular
programming languages.
You can supply custom error leak keywords using the `--errorLeakKeywords <FILE>` argument.

The provided keywords are search as substrings in the response body and if found, the response is marked as an error
leak. Example [errorLeaks.txt](./errorLeaks.txt) file contains some custom keywords that can be used.

## Paths ordering

By default, CATS runs paths in alphabetical order. You can override this behavior by providing a file with the desired
order using the `--pathsRunOrder <FILE>` argument. An example file [pathsOrder.txt](./pathsOrder.txt).




