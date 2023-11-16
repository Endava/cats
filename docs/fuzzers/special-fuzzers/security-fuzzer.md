---
sidebar_position: 2
description: Fuzz with custom dictionaries and discrete configuration
---

# Security Fuzzer
You can use CATS with your own dictionary by fuzzing specific fields with different sets of [nasty strings](https://github.com/minimaxir/big-list-of-naughty-strings).
The behaviour is similar to the `FunctionalFuzzer` using `cats run <file-name>` sub-command. 
You can use the exact same elements for output variables, test correlation, verify responses and so forth, with the addition that you must also specify a `targetFields` and/or `targetFieldTypes` and a `stringsList` element.
A typical `SecurityFuzzer` file will look like this:

```yaml
/pet:
    test_1:
      description: Run XSS scenarios
      name: "My Pet"
      expectedResponseCode: 200
      httpMethod: all
      targetFields:
        - pet#id
        - pet#description
      stringsFile: xss.txt
```

And a typical run:

```shell
cats run securityFuzzerFile.yml -c contract.yml -s http://localhost:8080
```

You can also supply `output`, `httpMethod`, `oneOfSelection` and/or `verify` (with the same behaviour as within the `FunctionalFuzzer`) if they are relevant to your case.

The file uses [Json path](https://github.com/json-path/JsonPath) syntax for all the properties you can supply; you must separate elements through `#` as in the example instead of `.`.

This is what the `SecurityFuzzer` will do after parsing the above `SecurityFuzzer` file:
- it will add the fixed value "My Pet" to all the request for the field `name` (act as a reference data)
- for each field specified in the `targetFields` i.e. `pet#id` and `pet#description` it will create a test for each line from the `xss.txt` file
- if you consider the `xss.txt` sample file included in the `CATS` repo, this means that it will send 21 requests targeting `pet#id` and 21 requests targeting `pet#description` i.e. a total of **42 tests**
- for each of these 42 tests, the `SecurityFuzzer` will expect a `200` response code (i.e. will report the match as a `success`). If another response code is returned, then `CATS` will report the test as `error`.

:::info
It was a deliberate choice to not include all fields by default.
:::

If you want the above logic to apply to all paths, you can use `all` as the path name:

```yaml
all:
    test_1:
      description: Run XSS scenarios
      name: "My Pet"
      expectedResponseCode: 200
      httpMethod: all
      targetFields:
        - pet#id
        - pet#description
      stringsFile: xss.txt
```

When using this fuzzer you must explicitly specify what is the target of fuzzing. You have multiple options.

## Targeting specific fields
You can target specific fields using the `targetFields` element:

```yaml
all:
    test_1:
      description: Run XSS scenarios on specific fields
      name: "My Pet"
      expectedResponseCode: 200
      httpMethod: all
      targetFields:
        - pet#id
        - pet#description
      stringsFile: xss.txt
```

This will iterate through all fields mentioned in the `targetFields` elements and replace their value with each line from the `xss.txt` file. 

## Targeting specific field types
You can target specific fields using the `targetFieldTypes` element and specifying the [OpenAPI type](https://swagger.io/docs/specification/data-models/data-types/) you are willing to fuzz.

```yaml
all:
    test_1:
      description: Run XSS scenarios on string fields
      name: "My Pet"
      expectedResponseCode: 200
      httpMethod: all
      targetFieldTypes:
        - string
      stringsFile: xss.txt
```

This will iterate through all the `string` fields and replace their values with each line from the `xss.txt` file.

## Targeting http headers
You can target HTTP headers using the `targetFieldTypes` element and specifying the `http_headers` keyword.

```yaml
all:
    test_1:
      description: Run XSS scenarios on http headers
      name: "My Pet"
      expectedResponseCode: 200
      httpMethod: all
      targetFieldTypes:
        - http_headers
      stringsFile: xss.txt
```

This will iterate through all http headers and replace their values with each line from the `xss.txt` file.

## Targeting the entire http request body
You can target the entire http request body (i.e. replace it entirely with another payload) using the `targetFieldTypes` element and specifying the `http_body` keyword:

```yaml
all:
    test_1:
      description: Run XSS scenarios on http body
      name: "My Pet"
      expectedResponseCode: 200
      httpMethod: all
      targetFieldTypes:
        - http_body
      stringsFile: xss.txt
```

This will cycle the entire http request body and replace it with each line from the `xss.txt` file.

:::tip
You can split the [nasty strings](https://github.com/minimaxir/big-list-of-naughty-strings) into multiple files.
You can have a `sql_injection.txt`, a `xss.txt`, a `command_injection.txt` and so on. For each of these files, you can create a test entry in the `SecurityFuzzer` file where you target the fields that are relevant.
The `expectedResponseCode` should be tweaked according to your particular context.
Your service might sanitize data before validation, so might be perfectly valid to expect a `200` or might validate the fields directly, so might be perfectly valid to expect a `400`.
A `500` will usually mean something was not handled properly and might signal a possible bug.
:::

## Working with additionalProperties in SecurityFuzzer
You can also set `additionalProperties` fields through the `functionalFuzzerFile` using the same syntax as for [Setting additionalProperties in Reference Data](/docs/getting-started/reference-data-file#setting-additionalproperties).

## SecurityFuzzer Reserved keywords
The following keywords are reserved in `SecurityFuzzer` tests: `output`, `expectedResponseCode`, `httpMethod`, `description`, `verify`, `oneOfSelection`, `targetFields`, `targetFieldTypes`, `stringsFile`, `checkBoolean`, `additionalProperties`, `topElement` and `mapValues`.

