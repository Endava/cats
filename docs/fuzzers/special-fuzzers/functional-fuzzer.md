---
sidebar_position: 1
description: The Fuzzer used to write functional tests without coding
---

# Functional Flow & Functional Fuzzer

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `FunctionalFlowFuzzer` / `FunctionalFuzzer` |
| **Log Key** | N/A; step names, test identifiers, and scenarios from the input file identify executions. |
| **Description** | Executes sequential multi-step functional flows (`FunctionalFlowFuzzer`) or path-grouped functional tests (`FunctionalFuzzer`) while preserving CATS payload generation and self-healing behavior. |
| **Enabled by default?** | No. Enable it with `cats run <file>`. |
| **Target fields** | Fields, path parameters, query parameters, headers, cross-field correlations, and request bodies specified by the functional YAML file. |
| **Expected result** | The `status`/`expect` (or `expectedResponseCode`/`verify`) assertions configured for each step or test. |
| **Fuzzing logic** | Generates a valid base request from the OpenAPI contract, applies step overrides and `correlate` rules, executes steps sequentially, captures variables (`save`/`output`), and evaluates response assertions. |
| **Conditions when this fuzzer will be skipped** | When no functional file is supplied, or a configured step/path does not match any OpenAPI operation. |
| **HTTP methods that will be skipped** | A method is skipped when it is invalid or unavailable on the selected OpenAPI path. |
| **Reporting** | Reports the configured status expectation, schema validation, and any `expect` / `verify` assertion failures. |

You can leverage CATS's self-healing payload generation to write end-to-end functional flows without coding using `cats run <file-name>`.

CATS automatically detects two YAML formats:
1. **Sequential `FunctionalFlowFuzzer` format (`steps:`)** — the recommended format for multi-step functional flows, cross-field correlations, and rich response assertions.
2. **Legacy path-grouped `FunctionalFuzzer` format** — supported for backwards compatibility (logs a deprecation warning when executed).

## Sequential `steps:` Format (`FunctionalFlowFuzzer`)

A sequential functional flow file defines optional `variables`, optional cross-field `correlate` rules, and an ordered list of HTTP `steps`:

```yaml
variables:
  prefix: "Rule"
  correlationId: "#(uuid)"

correlate:
  - paths: ["/risk-rules*"]
    when: "ruleType == 'STATIC'"
    then:
      omit: ["metricField"]
  - paths: ["/orders*"]
    when: "items[*].category == 'DIGITAL'"
    then:
      set:
        items[*].shippingRequired: false

steps:
  - name: Create static rule
    POST: /risk-rules
    headers:
      X-Correlation-Id: "${correlationId}"
    base: MERGE
    omit: [id]
    body:
      ruleType: "STATIC"
      name: "${prefix}-Alpha"
      metadata:
        owner: "risk-team"
    status: 201
    save:
      createdRuleId: ruleId
      serverHeader: header#X-Server
    expect:
      ruleType: "STATIC"
      headers:
        X-Server: "risk.*"
      bodyContains: ["rule_"]
      bodyNotContains: ["stacktrace"]
      where:
        - "priority >= 10 & $request.ruleType = 'STATIC'"
        - "responseTimeMs < 1000 & status == 201"

  - name: Patch created rule using inline path and query parameters
    PATCH: /risk-rules/${createdRuleId}?dryRun=false
    body:
      active: false
    status: 200
    expect:
      ruleId: "${createdRuleId}"
      active: false
```

Run the flow with:

```shell
cats run flow.yml -c contract.yml -s http://localhost:8080
```

### Step properties in `steps:`
- **HTTP method and target path**: Either use a shorthand method key such as `POST: /risk-rules` or `PATCH: /risk-rules/${createdRuleId}?dryRun=false` (inline path parameters and query parameters are extracted automatically), or specify `method: POST` and `path: /risk-rules`.
- **`name`** (or `description`): Scenario name shown in logs and reports.
- **`base`** (or `mode`): Controls how the starting payload is generated from the OpenAPI schema before `body` overrides are applied:
  - `MERGE` (default): Starts from a full generated happy-path payload and merges `body` overrides.
  - `ONLY_REQUIRED`: Starts from a payload containing only required fields.
  - `EMPTY`: Starts from an empty `{}` payload.
- **`body`**: Map, list, or raw scalar overriding the request body. Nested maps are merged into existing objects; dot/hash paths (such as `metadata.owner` or `metadata#owner`) are also supported.
- **`path`**, **`query`**, **`headers`**: Maps of path parameters, query parameters, and HTTP headers for the step.
- **`omit`**: List of fields to remove from the generated request payload.
- **`variant`**: Selects a specific `oneOf`/`anyOf` schema variant by schema name suffix (string) or discriminator field map (object).
- **`correlate`**: Step-level cross-field correlation rules applied after `body` and `omit`.
- **`save`** (or `output`): Captures variables from the exchange for use in later steps as `${varName}`:
  - `field.path` or `$response.field.path`: Extracts a JSON property from the response body.
  - `$`, `$body`, or `$response.body`: Captures a root scalar response body.
  - `header#Header-Name` or `header.Header-Name`: Captures a response header.
  - `$request.field.path` or `$path.paramName`: Captures a value from the sent request body or path parameters.
- **`status`** and **`expect`** (or `verify`):
  - `status`: Expected status code(s) or family (e.g. `201`, `[200, 201]`, `2XX`). Defaults to `2XX`.
  - `fields` (or any non-reserved key directly under `expect`): Exact value or Java regex assertions on response body fields.
  - `headers`: Exact value or regex assertions on response headers.
  - `bodyContains` / `bodyNotContains`: Substrings that must or must not appear in the raw response body.
  - `where`: Boolean correlation expressions across response fields, `$request.*` fields, `status`, `time` / `responseTimeMs`, `size(arrayField)`, `is null` / `is not null`, `contains`, and `matches`, combined with `&` / `&&` and `|` / `||`.
  - `matchesSchema`: Whether to validate the response body against the OpenAPI response schema (default: `true`).

### Reusing Cross-Field Correlations Across All Fuzzers (`--correlations`)

You can also apply `correlate:` rules (`when`/`then` `set` or `omit`, including per-element wildcard array `[*]` rules) during normal OpenAPI fuzzing, `cats run`, `cats replay`, `cats template`, and `cats random` by passing `--correlations` (or `--correlate`):

```shell
cats -c contract.yml -s http://localhost:8080 --correlations correlations.yml
```

During fuzzing, CATS automatically protects the field currently targeted by a fuzzer so correlation rules never overwrite the fuzzer's negative mutation.

---

## Legacy Path-Grouped Format (`FunctionalFuzzer`)

:::caution Deprecated format
The legacy path-grouped `FunctionalFuzzer` YAML format is deprecated and will be removed in a future release. Please prefer the sequential `steps:` format above for new functional flows.
It's also important to note that `--refData` won't get replaced when using the `FunctionalFuzzer`; any reference data must be supplied in the `FunctionalFuzzer` file (or generated with `--createRefData`).
:::

The legacy `FunctionalFuzzer` file has the following syntax:

```yaml
/path:
    testNumber:
        description: Short description of the test
        prop: value
        prop#subprop: value
        prop7:
          - value1
          - value2
          - value3
        oneOfSelection:
          element#type: "Value"
        expectedResponseCode: HTTP_CODE
        httpMethod: HTTP_METHOD
```

## Global variables and path variables

Define values once under `cats-global-vars` and reference them from any test:

```yaml
cats-global-vars:
  environment: "demo"
  tenant: "tenant-${environment}"
  generatedId: "#(uuid)"

/pets/{id}:
  get_pet:
    id: ${generatedId}
    tenant: ${tenant}
    expectedResponseCode: 200
    httpMethod: GET
```

Place a global variable after any variables it references. Placeholders can be
embedded in a larger value, so the example above resolves `tenant` to
`tenant-demo`.

Values captured from a response can be reused through `output` as before. Path
variables can also be captured explicitly with `$path`, while request values
can be referenced with `$request` in output and verification expressions.

Placeholders can be embedded in functional request-body values and `verify`
values. For example, `Bearer ${accessToken}` combines literal text with a value
captured by an earlier test.

The `scenario` keyword is preferred for the displayed test scenario; `description`
remains supported for compatibility.

And a typical run will look like (supposing the above file is named `functionalFuzzer.yml`):

```shell
cats run functionalFuzzer.yml -c contract.yml -s http://localhost:8080
```

This is a description of the elements within the `functionalFuzzer.yml` file:
- you can supply a `description` of the test. This will be set as the `Scenario` description. If you don't supply a `description` the `testNumber` will be used instead.
- you can have multiple tests under the same path: `test1`, `test2`, etc.
- `expectedResponseCode` is mandatory, otherwise the `Fuzzer` will ignore this test. The `expectedResponseCode` tells CATS what to expect from the service when executing this test.
- *at most* one of the properties can have multiple values. When this situation happens, that test will actually become a list of tests one for each of the values supplied. For example in the above example `prop7` has 3 values. This will actually result in 3 tests, one for each value.
- the tests are executed **in the declared order**. This is why you can have outputs from one test act as inputs for the next one(s) (see the next section for details).
- if the supplied `httpMethod` doesn't exist in the OpenAPI given path, a `warning` will be issued and the test won't be executed
- if the supplied `httpMethod` is not a valid HTTP method, a `warning` will be issued and the test won't be executed
- if the request payload uses a `oneOf` element to allow multiple request types, you can control which of the possible types the `FunctionalFuzzer` will apply to using the `oneOfSelection` keyword. The value of the `oneOfSelection` keyword must match the fully qualified name of the `discriminator`.
- if no `oneOfSelection` is supplied, and the request payload accepts multiple `oneOf` elements, then a test will be created for each type of payload
- the file uses [Json path](https://github.com/json-path/JsonPath) syntax for all the properties you can supply; you can separate elements through `#` or `.`

## Dealing with oneOf, anyOf
When you have request payloads which can take multiple object types, you can use the `oneOfSelection` keyword to specify which of the possible object types is required by the `FunctionalFuzzer`.
If you don't provide this element, all combinations will be considered. If you supply a value, **this must be exactly the one used in the `discriminator`.**

## Correlating Tests
Suppose we have an endpoint that creates data (doing a `POST`), and we want to check its existence (via `GET`).
We need a way to get some identifier from the POST call and send it to the GET call.
The `FunctionalFuzzer` input file can have an `output` entry where you can state a variable name, and its fully qualified name from the response in order to set its value.
You can then refer the variable using `${variable_name}` from a subsequent test in order to use its value.

An output selector normally reads from the response body. Prefix a response
header with `header#` to capture its value instead:

```yaml
/sessions:
  create_session:
    scenario: Create a session and capture its token
    httpMethod: POST
    expectedResponseCode: 201
    output:
      sessionId: id
      accessToken: header#X-Access-Token
```

Output variables are also available to headers supplied with `-H` or
`--headers`, and to query parameters supplied with `--queryParams`. This allows
later tests in the same functional run to use values such as
`Authorization: Bearer ${accessToken}` or `session_id=${sessionId}`. When using
an inline header, quote the value so the shell does not expand the placeholder:

```shell
cats run functionalFuzzer.yml -c contract.yml -s http://localhost:8080 \
  -H 'Authorization=Bearer ${accessToken}'
```

For per-path headers or query parameters, put the same placeholders in the YAML
file passed to `--headers` or `--queryParams`.

Here is an example:

```yaml
/pet:
    test_1:
      description: Create a Pet
      httpMethod: POST
      name: "My Pet"
      expectedResponseCode: 200
      output:
        petId: pet#id
/pet/{id}:
    test_2:
      description: Get a Pet
      id: ${petId}
      expectedResponseCode: 200
```

Suppose the `test_1` execution outputs:

```json
{
  "pet": 
    { 
      "id" : 2
    }
}
```

When executing `test_1` the value of the pet id will be stored in the `petId` variable (value `2`).
When executing `test_2` the `id` parameter will be replaced with the `petId` variable (value `2`) from the previous case.

:::caution
Variables are visible across all tests i.e. global variables; please be careful with the naming as they will get overridden.
:::

## Verifying Responses
The `FunctionalFuzzer` can verify more than just the `expectedResponseCode`. This is achieved using the `verify` element. This is an extended version of the above `functionalFuzzer.yml` file.

```yaml
/pet:
    test_1:
      description: Create a Pet
      httpMethod: POST
      name: "My Pet"
      expectedResponseCode: 200
      output:
        petId: pet#id
      verify:
        pet#name: "Baby"
        pet#id: "[0-9]+"
/pet/{id}:
    test_2:
      description: Get a Pet
      id: ${petId}
      expectedResponseCode: 200
```

When running the above file:
- the `FunctionalFuzzer` will check if the response has the 2 elements `pet#name` and `pet#id`
- if the elements are found, it will check that the `pet#name` has the `Baby` value and that the `pet#id` is numeric

The following json response will pass `test_1`:

```json
{
  "pet": 
    { 
      "id" : 2,
      "name": "Baby"
    }
}
```

But this one won't (`pet#name` is missing):
```json
{
  "pet": 
    { 
      "id" : 2
    }
}
```

You can also refer to request fields in the `verify` section by using the `${request#..}` qualifier, and to previously captured variables with `${variableName}`. Using the above example, by having the following `verify` section:

```yaml
/pet:
  test_1:
    description: Create a Pet
    httpMethod: POST
    name: "My Pet"
    expectedResponseCode: 200
    output:
      petId: pet#id
    verify:
      pet#name: "${request#name}"
      pet#authorization: "Bearer ${accessToken}"
      pet#id: "[0-9]+"
```

It will verify if the response contains a `pet#name` element and that its value equals `My Pet` as sent in the request.

You can also supply boolean expression to be checked in the verify section using the `checkBoolean` keyword:

```yaml
/pet:
  test_1:
    description: Create a Pet
    httpMethod: POST
    name: "My Pet"
    expectedResponseCode: 200
    output:
      petId: pet#id
    verify:
      pet#name: "${request#name}"
      pet#id: "[0-9]+"
      checkBoolean: T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))
```

:::note
The `checkBoolean` example also uses a [SpEL expression](/docs/advanced-topics/dynamic-values) to check that the returned `expiry` is after the current date.
:::

Dynamic expressions can be supplied in any section of the test case: either as input data (for example you might need a random identifier each time the test will run)
in the output sections (you might want to format the response fields before storing them in a variable) or in the verify section (applying different transformations before asserting).
Dynamic expressions can also refer variables or request/response fields internally. In the example above, `expiry` is a field returned in the response.
If you want to refer a variable created in a previous test, let's call it `petName`, you can do so as: `T(org.apache.commons.lang3.StringUtils).substringAfterLast(${petName},'a')`.
You can also refer request elements using `${request#field}`. 

Important notes:
- `verify` parameters support Java regexes as values
- you can supply more than one parameter to check (as seen above)
- if at least one of the parameters is not present in the response, CATS will report an `error`
- if all parameters are found and have valid values, but the response code is not matched, CATS will report a `warning`
- if all the parameters are found and match their values, and the response code is as expected, CATS will report a `success`

## Working with additionalProperties in FunctionalFuzzer
You can also set `additionalProperties` fields through the `FunctionalFuzzer` file using the same syntax as for [Setting additionalProperties in Reference Data](/docs/getting-started/reference-data-file#setting-additionalproperties).

## Reserved Keywords
The following keywords are reserved in `FunctionalFuzzer` tests: `output`, `expectedResponseCode`, `httpMethod`, `scenario`, `description`, `oneOfSelection`, `verify`, `checkBoolean`, `checkTrue`, `checkFalse`, `additionalProperties`, `topElement` and `mapValues`.
