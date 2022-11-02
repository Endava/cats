---
sidebar_position: 1
description: The Fuzzer used to write functional tests without coding
---

# Functional Fuzzer

You can leverage CATS super-powers of self-healing and payload generation in order to write functional tests.
This is achieved using the so called `FunctionaFuzzer` using the `cats run <file-name>` sub-command. 
The `FunctionalFuzzer` is not a `Fuzzer` per se, but was named as such for consistency.
The functional tests are written in a YAML file using a simple DSL.
The DSL supports adding identifiers, descriptions, assertions as well as passing variables between tests.
The cool thing is that, by leveraging the fact that CATS generates valid payload, you only need to override values for specific fields.
The rest of the information will be populated by CATS using valid data, just like a `happy` flow request.

:::caution
It's important to note that `--refData` won't get replaced when using the `FunctionalFuzzer`.
Any reference data must be supplied in the `FunctionalFuzzer` file.
:::

The `FunctionalFuzzer` file has the following syntax:

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
        httpMethod: HTTP_NETHOD
```

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
- the file uses [Json path](https://github.com/json-path/JsonPath) syntax for all the properties you can supply; you must separate elements through `#` as in the example above instead of `.`

## Dealing with oneOf, anyOf
When you have request payloads which can take multiple object types, you can use the `oneOfSelection` keyword to specify which of the possible object types is required by the `FunctionalFuzzer`.
If you don't provide this element, all combinations will be considered. If you supply a value, **this must be exactly the one used in the `discriminator`.**

## Correlating Tests
Suppose we have an endpoint that creates data (doing a `POST`), and we want to check its existence (via `GET`).
We need a way to get some identifier from the POST call and send it to the GET call.
The `FunctionalFuzzer` input file can have an `output` entry where you can state a variable name, and its fully qualified name from the response in order to set its value.
You can then refer the variable using `${variable_name}` from a subsequent test in order to use its value.

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

You can also refer to request fields in the `verify` section by using the `${request#..}` qualifier. Using the above example, by having the following `verify` section:

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

Important notes:
- `verify` parameters support Java regexes as values
- you can supply more than one parameter to check (as seen above)
- if at least one of the parameters is not present in the response, CATS will report an `error`
- if all parameters are found and have valid values, but the response code is not matched, CATS will report a `warning`
- if all the parameters are found and match their values, and the response code is as expected, CATS will report a `success`

## Working with additionalProperties in FunctionalFuzzer
You can also set `additionalProperties` fields through the `FunctionalFuzzer` file using the same syntax as for [Setting additionalProperties in Reference Data](/docs/getting-started/reference-data-file#setting-additionalproperties).

## Reserved Keywords
The following keywords are reserved in `FunctionalFuzzer` tests: `output`, `expectedResponseCode`, `httpMethod`, `description`, `oneOfSelection`, `verify`, `checkBoolean`, `additionalProperties`, `topElement` and `mapValues`.


