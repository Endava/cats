![CI](https://github.com/Endava/cats/workflows/CI/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=alert_status)](https://sonarcloud.io/dashboard?id=cats)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=sqale_index)](https://sonarcloud.io/dashboard?id=cats)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=coverage)](https://sonarcloud.io/dashboard?id=cats)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=cats)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=cats)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=bugs)](https://sonarcloud.io/dashboard?id=cats)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=cats&metric=code_smells)](https://sonarcloud.io/dashboard?id=cats)



![CATS](images/cats.png)

Table of Contents
=================

   * [Contract driven Auto-generated Tests for Swagger](#contract-driven-auto-generated-tests-for-swagger)
   * [How the Fuzzing works](#how-the-fuzzing-works)
   * [Build](#build)
   * [Available commands](#available-commands)
   * [Running CATS](#running-cats)
      * [Notes on Unit Tests](#notes-on-unit-tests)
   * [Available arguments](#available-arguments)
   * [Available Fuzzers](#available-fuzzers)
      * [BooleanFieldsFuzzer](#booleanfieldsfuzzer)
      * [DecimalFieldsLeftBoundaryFuzzer and DecimalFieldsRightBoundaryFuzzer](#decimalfieldsleftboundaryfuzzer-and-decimalfieldsrightboundaryfuzzer)
      * [IntegerFieldsLeftBoundaryFuzzer and IntegerFieldsRightBoundaryFuzzer](#integerfieldsleftboundaryfuzzer-and-integerfieldsrightboundaryfuzzer)
      * [ExtremeNegativeValueXXXFieldsFuzzer and ExtremePositiveValueXXXFuzzer](#extremenegativevaluexxxfieldsfuzzer-and-extremepositivevaluexxxfuzzer)
      * [LargeValuesInHeadersFuzzer](#largevaluesinheadersfuzzer)
      * [RemoveFieldsFuzzer](#removefieldsfuzzer)
         * [POWERSET](#powerset)
         * [ONEBYONE](#onebyone)
         * [SIZE](#size)
      * [HappyFuzzer](#happyfuzzer)
      * [RemoveHeadersFuzzer](#removeheadersfuzzer)
      * [HttpMethodsFuzzer](#httpmethodsfuzzer)
      * [BypassAuthenticationFuzzer](#bypassauthenticationfuzzer)
      * [StringFormatAlmostValidValuesFuzzer](#stringformatalmostvalidvaluesfuzzer)
      * [StringFormatTotallyWrongValuesFuzzer](#stringformattotallywrongvaluesfuzzer)
      * [NewFieldsFuzzer](#newfieldsfuzzer)
      * [StringsInNumericFieldsFuzzer](#stringsinnumericfieldsfuzzer)
      * [CustomFuzzer](#customfuzzer)
         * [Correlating Tests](#correlating-tests)
   * [Skipping Fuzzers for specific paths](#skipping-fuzzers-for-specific-paths)
   * [Reference Data File](#reference-data-file)
   * [Headers File](#headers-file)
   * [URL Params](#url-params)
   * [Edge Spaces Strategy](#edge-spaces-strategy)
   * [URL Parameters](#url-parameters)
   * [Dealing with AnyOf, AllOf and OneOf](#dealing-with-anyof-allof-and-oneof)
   * [Limitations](#limitations)
      * [Inheritance and composition](#inheritance-and-composition)
      * [Additional Parameters](#additional-parameters)
      * [Regexes within 'pattern'](#regexes-within-pattern)
   * [Contributing](#contributing)

# Contract driven Auto-generated Tests for Swagger
Automation testing is cool, but what if you could automate testers? More specifically, what if you could automate **all** of the process of writing test cases, getting test data, writing the automation tests and then running them?  This is what CATS does.

CATS is a tool that **generates tests at runtime** based on a given OpenAPI contract. It will also automatically run those tests against a given service instance to check if the API has been implemented in accordance with its contract.   Think of it as a tool that **eliminates the boring testing** activities from contract and API testing, allowing you to **focus on creative activities**.

The tests are generated based on configured `Fuzzer`s. Each `Fuzzer` will test several scenarios and report the resulting behaviour in both the console and in the generated test report.

The following logging levels are used (in both the console and the test report) to report the testing activity:

- `INFO` will report normal documented behaviour. This is expected behaviour. No need for action.
- `WARN` will report normal but undocumented behaviour or some misalignment between the contract and the service. This will **ideally** be actioned.
- `ERROR` will report abnormal/unexpected behaviour. This **must** be actioned.


# How the Fuzzing works
CATS will iterate through all endpoints, all HTTP methods and all the associated requests bodies and parameters and `fuzz` their data models fields values according to their defined data type and constraints. The actual fuzzing depends on the specific `Fuzzer` executed. Please see the list of fuzzers and their behaviour.
There are also differences on how the fuzzing works depending on the HTTP method:

- for methods with request bodies like POST, PUT the fuzzing will be applied at the request body data models level
- for methods without request bodies like GET, DELETE the fuzzing will be applied at the URL parameters level

This means that for methods with request bodies you need to supply the `path` parameters via `urlParams` or the `referenceData` file as failure to do so will result in `Illegal character in path at index ...` errors. 


# Build

You can use the following Maven command to build the project:

`mvn clean package`

This will output a `cats.jar` file in the current directory. The file is an executable JAR that will run in Linux environments. Just run `chmod +x cats.jar` to make the file executable.

# Available commands
To list all available commands, run:
`./cats.jar commands`

Other ways to get help from the CATS command are as follows:

- `./cats.jar help` will list all available options

- `./cats.jar version` will display the current CATS version

- `./cats.jar list fuzzers` will list all the existing fuzzers

- `./cats.jar list fieldsFuzzingStrategy` will list all the available fields fuzzing strategies

- `./cats.jar list paths --contract=CONTRACT` will list all the paths available within the contract

# Running CATS
A minimal run must provide the Swagger/OpenApi contract and the URL address of the service:

`./cats.jar --contract=mycontract.yml --server=https://localhost:8080`

But there are multiple other arguments you can supply. More details in the next section.

## Notes on Unit Tests

You may see some `ERROR` log messages while running the Unit Tests. Those are expected behaviour for testing the negative scenarios of the `Fuzzers`.

# Available arguments
- `--contract=LOCATION_OF_THE_CONTRACT` supplies the location of the OpenApi or Swagger contract.
- `--server=URL` supplies the URL of the service implementing the contract.
- `--basicauth=USR:PWD` supplies a `username:password` pair, in case the service uses basic auth (more auth schemes will follow in future releases).
- `--fuzzers=LIST_OF_FUZZERS` supplies a comma separated list of fuzzers. If the argument is not supplied all fuzzers will be run.
- `--log=PACKAGE:LEVEL` can configure custom log level for a given package. This is helpful when you want to see full HTTP traffic: `--log=org.apache.http.wire:debug`
- `--paths=PATH_LIST` supplies a `;` list of OpenApi paths to be tested. If no path is supplied, all paths will be considered.
- `--fieldsFuzzingStrategy=STRATEGY` specifies which strategy will be used for field fuzzing. Available strategies are `ONEBYONE`, `SIZE` and `POWERSET`. More information on field fuzzing can be found in the sections below.
- `--maxFieldsToRemove=NUMBER` specifies the maximum number of fields to be removed when using the `SIZE` fields fuzzing strategy.
- `--refData=FILE` specifies the file containing static reference data which must be fixed in order to have valid business requests. This is a YAML file. It is explained further in the sections below.
- `--headers=FILE` specifies a file containing headers that will be added when sending payloads to the endpoints. You can use this option to add oauth/JWT tokens for example.
- `--reportingLevel=LEVEL` specifies which reporting level you want to use. It can be `INFO`, `WARN` or `ERROR`. You can use `WARN` or `ERROR` to filter the tests that are passing and focus only on the ones that fail
- `--edgeSpacesStrategy=STRATEGY` specifies how to expect the server to behave when sending trailing and prefix spaces within fields. Possible values are `trimAndValidate` and `validateAndTrim`.
- `--urlParams` A ';' separated list of 'name:value' pairs of parameters to be replaced inside the URLs. This is useful when you have static parameters in URLs (like 'version' for example).
- `--customFuzzerFile` a file used by the `CustomFuzzer` that will be used to create user-supplied payloads.
- `--skipXXXForPath=path1,path2` can configure a fuzzer to be skipped for the specified paths. You must provide a full `Fuzzer` name instead of `XXX`. For example: `--skipVeryLargeStringsFuzzerForPath=/path1,/path2`

Using some of these options a typical invocation of CATS might look like this:

`./cats.jar --contract=my.yml --server=https://locathost:8080 --log=org.apache.http.wire:debug`

# Available Fuzzers
To get a list of fuzzers just run `./cats.jar list fuzzers`. A list of all of the available fuzzers will be returned, along with a short description for each.
Some of the `Fuzzers` are also detailed below. 

## BooleanFieldsFuzzer
This `Fuzzer` applies only to Boolean fields. It will try to send invalid boolean values and expects a `4XX` response code.

## DecimalFieldsLeftBoundaryFuzzer and DecimalFieldsRightBoundaryFuzzer
This `Fuzzer` will run boundary tests for fields marked as `Number`, including `float` and `double` formats.  It will use the `minimum` property to generate a left boundary value or `maximum` for the right boundary one.
If any of these values are not set, it will use `Long.MIN_VALUE` and `Long.MAX_VALUE`. It expects a `4XX` response code.

## IntegerFieldsLeftBoundaryFuzzer and IntegerFieldsRightBoundaryFuzzer
This `Fuzzer` is similar to the `Decimal Fuzzers`, but for `Integer` fields, both `int32` and `int64` formats.

## ExtremeNegativeValueXXXFieldsFuzzer and ExtremePositiveValueXXXFuzzer
These `Fuzzers` apply for `Decimal` and `Integer` fields. They will send either an extremely low negative value or an extremely high positive value as follows:
- for `Decimal` fields: `-999999999999999999999999999999999999999999.99999999999`when no format is specified, and `-Float.MAX_VALUE` for `float` and `-Double.MAX_VALUE` for `double`
- for `Integer` fields: `Long.MIN_VALUE` when no format is specified or `int32`and `2 * Long.MIN_VALE` for `int64`

These `Fuzzers` expect a `4XX` response code.

## LargeValuesInHeadersFuzzer
This `Fuzzer` will send large values in the request headers. It will iterate through each header and fuzz it with a large value. All the other headers and the request body and query string will be similar to a 'normal' request. This `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with a `4XX` code. In case the response code is a documented one, this will be reported with an `INFO` level log message, otherwise with a `WARN` level message.
- If the service responds with a `2XX` code, the `Fuzzer` will report it as an `ERROR` level message.
- Any other case will be reported using an `ERROR` level message.


## RemoveFieldsFuzzer
This `Fuzzer` will remove fields from the requests based on a supplied strategy. It will create subsets of all the fields and subfields within the request schema. Based on these subsets, it will:
- iterate through them one by one 
- remove the fields present in the current subset from a full service payload
- send the modified request to the server 

These subsets can be generated using the following strategies (supplied through the `--fieldsFuzzingStrategy` option):

### POWERSET
This is the most time consuming strategy. This will create all possible subsets of the request fields (including subfields). If the request contains a lot of fields, this strategy might not be the right choice as the toal number of possibilities is `2^n`, where `n` is the number of fields.

For example given the request:

```json

{
    "address": {
        "phone": "123",
        "postCode": "408",
        "street": "cool street"    
    },
    "name": "john"
}

```

All the fields, including subfields will look like this: `{name, address#phone, address#postcode, address#street}`. Using the `POWERSET` strategy there are 16 possible subsets. The `FieldsFuzzer` will iterate through each set and remove those fields (and subfields) from the request. All the other headers and request fields will remain unchanged.

### ONEBYONE
This is the faster strategy and also the **default** one. This will iterate though each request field (including subfields) and create a single element set from it. The `FieldFuzzer` will iterate though the resulting sets and remove those fields (and subfields) from the request i.e. one field at a time. All the other headers and fields will remain unchanged.

If we take the example above again, the resulting sets produced by this strategy will be as follows:

`{address#phone}, {address#postcode}, {address#street}, {name}`

### SIZE
This is a mixed strategy. It applies principles from the `POWERSET` strategy, but will remove a maximum number of fields (and subfields) supplied though the `--maxFieldsToRemove` option. This means that will generate subsets of fields (and subfields) having size `n - maxFieldsToRemove` or greater, where `n` is the total number of fields and subfields.

If `--maxFieldsToRemove` for the example above is `2`, the resulting sets produced by this strategy will be as follows:

`
{address#phone, address#postcode, address#street}, {name, address#postcode, address#street}, {name, address#phone, address#street}, {name, address#phone, address#postcode}, {name, address#street}, {name, address#postalcode}, {name, address#phone}, {address#phone, address#postalcode}, {address#phone, address#street}, {address#postalcode, address#street}
`

Independent of the strategy used to generate the subsets of the fields that will be iteratively removed, the `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with `4XX` in cases where required fields (or subfields) were removed and with a `2XX` code in cases where optional fields (or subfields) were removed. If the response code received is a documented one, this will be logged with an `INFO` level log message, otherwise with a `WARN` message.
- In the case when the request has at least one required field removed and the service responds with `2XX` this will be reported using an `ERROR` message.
- In the case when the request didn't have any required field (or subfield) removed and the service responds with `2XX`, this is expected behaviour and will be reported using an `INFO` level message.
- In the case when the request didn't have any required field removed, but the service responds with a `4XX` or `5XX` code, this is abnormal behaviour and will be reported as an `ERROR` message.
- Any other case is considered abnormal behaviour and will be reported as an `ERROR` message.

## HappyFuzzer
This `Fuzzer` will send a full request to the service, including all fields and headers. The `Fuzzer` will behave as follows:
- Normal behaviour is for the service to return a `2XX` code. This will be reported as an `INFO` message if it's a documented code or as a `WARN` message otherwise.
- Any other case is considered abnormal behaviour and will be reported as an `ERROR` message.

## RemoveHeadersFuzzer
This `Fuzzer` will create the Powerset of the headers set. It will then iterate through all those sets and remove them from the payload. The `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with a `4XX` code in the case when required headers were removed and with a `2XX` code in the case of optional headers being removed. If the response code is a documented one, this will be reported as an `INFO` level message, otherwise as a `WARN` message.
- In the case that the request has at least one required header removed and the service responds with a `2XX` code, this will be reported as an `ERROR` message.
- In the case that the request didn't have any required headers removed and the service response is a `2XX` code, this is expected behaviour and will be reported as an `INFO` level log message.
- In the case where the request didn't have any required headers removed, but the service responded with a `4XX` or `5XX` code, this is abnormal behaviour and will be reported as an `ERROR` message.
- Any other case is considered abnormal behaviour and will be reported as an `ERROR` message.

Please note: **When the RemoveHeadersFuzzer is running any security (either named `authorization` or `jwt`) header mentioned in the `headers.yml` will be added to the requests.**

## HttpMethodsFuzzer
This `Fuzzer` will set the http request for any unspecified HTTP method in the contract. The `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with a `405` code if the method is not documented in the contract. This is reported as an level `INFO` message.
- If the service responds with a `2XX` code this is considered abnormal behaviour and will be reported as an `ERROR` message.
- Any other case is reported as a `WARN` level message.

## BypassAuthenticationFuzzer
This `Fuzzer` will try to send 'happy' flow requests, but will omit any supplied header which might be used for authentication like: `Authorization` or headers containing `JWT`.
The expected result is a `401` or `403` response code.

## StringFormatAlmostValidValuesFuzzer
OpenAPI offers the option to specify `formats` for each `string` field. This gives hints to the client on what type of data is expected by the API.
This `Fuzzer` has a predefined list of `formats`. For all `strings` matching any of the predefined `formats` it will send values which are 'almost valid' for that particular format. For example:
- if the `format` is `password` it will send the `string` `bgZD89DEkl` which is an almost valid strong password (except that it doesn't contain special characters).
- if the `format` is `email` it will send the `string` `email@bubu.` which is an almost valid email (except it doesn't contain the domain extension).
- and so on.
The following formats are supported: `byte, date, date-time, hostname, ipv4, ipv6, ip, password, uri, url, uuid`
The `Fuzzer` expects a `4XX` response code.

## StringFormatTotallyWrongValuesFuzzer
This behaves in the same way as the previous `Fuzzer`, but the values sent for each format are totally invalid (like `aaa` for `email` for example).

## NewFieldsFuzzer
This `Fuzzer` will inject new fields inside the body of the requests. The new field is called `fuzzyField`. The `Fuzzers` will behave as follows:
- Normal behaviour is for the service to return a `4XX` code for `POST`, `PUT` and `PATCH` and a `2XX` code for `GET`. If the code is documented, this will be reported as an `INFO` message, otherwise as a `WARN` message.
- If the code responds with a `2XX` or `4XX` code, depending on the previous point, this is considered abnormal behaviour and will reported as an `ERROR` message.
- Any other case is reported as an `ERROR` message.

## StringsInNumericFieldsFuzzer
This `Fuzzer` will send the `fuzz` string in every numeric fields and expect all requests to fail with `4XX`.

## CustomFuzzer
In some cases, the tests generated by CATS will not be sufficient for your situation. Using the `CustomFuzzer` you can supply custom values for specific fields. The cool thing is that you can target a single field, and the rest of the information will be sent just like a 'happy' flow request.
It's important to note that 'reference data' won't get replaced when using the `CustomFuzzer`. So if there are reference data fields, you must also supply those in the `CustomFuzzer`.
The `CustomFuzzer` will only trigger if a valid `customFuzzer.yml` file is supplied. The file has the following syntax:

```yaml
/path:
    testNumber:
        description: Short description of the test case
        prop: value
        prop#subprop: value
        prop7:
          - value1
          - value2
          - value3
        expectedResponseCode: HTTP_CODE
```

Some things to note about the `customFuzzer.yml` file:
- you can supply a `description` of the test case. This will be set as the `Scenario` description. If you don't supply a `description` the `testNumber` will be used instead.
- you can have multiple tests under the same path: `test1`, `test2`, etc.
- `expectedResponseCode` is mandatory, otherwise the `Fuzzer` will ignore this test. The `expectedResponseCode` tells CATS what to expect from the service when sending this test.
- *at most* one of the properties can have multiple values. When this situation happens, that test will actually become a list of tests one for each of the values supplied. For example in the above example `prop7` has 3 values. This will actually result in 3 tests, one for each value.
- `CustomFuzzer` only triggers when you supply a `customFuzzer.yml`-like file using the `--customFuzzerFile=XXX` argument.

### Correlating Tests
As CATs mostly relies on generated data with small help from some reference data, testing business scenarios with the pre-defined `Fuzzers` is not possible. Suppose we have an endpoint that creates data (doing a `POST`), and we want to check its existence (via `GET`).
We need a way to get some identifier from the POST call and send it to the GET call. This is now possible using the `CustomFuzzer`.
The `customFuzzerFile` can have an `output` entry where you can state a variable name, and its fully qualified name from the response in order to set its value. 
You can then refer the variable using `${variable_name}` from another test case in order to use its value. 
Here is an example:
```yaml
/pet:
    test_1:
      description: Create a Pet
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

When executing `test_1` the value of the pet id will be stored in the `petId` variable.
When executing `test_2` the `id` parameter will be replaced with the `petId` variable from the previous case.

**Some notes:**
- variables are visible across all custom tests; please be careful with the naming as they will get overridden
- variables do not support arrays of elements; this applies for both getting the variable value and the way the `output` variables are set

# Skipping Fuzzers for specific paths
There might be situations when you would want to skip some fuzzers for specific paths. This can be done using the `--skipXXXForPath=path1,path2` argument.
Some examples:
```bash
./cats.jar --contract=api.yml --server=http://localhost:8080 --skipVeryLargeStringsFuzzerForPath=/pet/{id},/pets
```

Running the above command will run all the fuzzers for all the paths, except for the `VeryLargeStringsFuzzer` which won't be run for the `/pet/{id}` and `/pets` paths.

You can supply multiple `--skipXXXForPath` arguments.

# Reference Data File
There are often cases where some fields need to contain relevant business values in order for a request to succeed. You can provide such values using a reference data file specified by the `--refData` argument. The reference data file is a YAML-format file that contains specific fixed values for different paths in the request document. The file structure is as follows:

```yaml
/path/0.1/auth:
    prop#subprop: 12
    prop2: 33
    prop3#subprop1#subprop2: "test"
/path/0.1/cancel:
    prop#test: 1
```

For each path you can supply custom values for properties and sub-properties which will have priority over values supplied by any other `Fuzzer`.
Consider this request payload:

```json

{
    "address": {
        "phone": "123",
        "postCode": "408",
        "street": "cool street"    
    },
    "name": "Joe"
}

```

and the following reference data file file:


```yaml
/path/0.1/auth:
    address#street: "My Street"
    name: "John"
```

This will result in any fuzzed request to the `/path/0.1/auth` endpoint being updated to contain the supplied fixed values:

```json

{
    "address": {
        "phone": "123",
        "postCode": "408",
        "street": "My Street"    
    },
    "name": "John"
}

```

# Headers File
This can be used to send custom fixed headers with each payload. It is useful when you have authentication tokens you want to use to authenticate the API calls. You can use path specific headers or common headers that will be added to each call using an `all` element. Specific paths will take precedence over the `all` element.
Sample headers file:

```yaml
all:
    Accept: application/json
/path/0.1/auth:
    jwt: XXXXXXXXXXXXX
/path/0.2/cancel:
    jwt: YYYYYYYYYYYYY
```

This will add the `Accept` header to all calls and the `jwt` header to the specified paths.

# URL Params
You can use `--urlParams` to send values for placeholders inside the contract paths. For example, if your contract paths look like: `/service/{version}/pets`, you can run cats as:
`./cats.jar --contract=api.yml --server=http://localhost:8080 --urlParams=version:v1.0`

so that each fuzzed path will replace `version` with `v1.0`. 

# Edge Spaces Strategy
There isn't a general consensus on how you should handle situations when you send leading and trailing spaces or leading and trailing valid values within fields. One strategy for the service will be to trim these values and consider them valid, while some other services will just consider them to be invalid. 
You can control how CATS should expect such cases to be handled by the service using the `--edgeSpacesStrategy` argument. You can set this to `error` or `success` depending on how you expect the service to behave:
- `error` means than the service will consider the values to be invalid, even if the value itself is valid, but has leading or trailing spaces.
- `success` means that the service will trim the value and validate it afterwards.

# URL Parameters
There are cases when certain parts of the request URL are parameterized. For example a case like: `/{version}/pets`. `{version}` is supposed to have the same value for all requests. This is why you can supply actual values to replace such parameters using the `urlParams` argument.
You can supply a `;` separated list of `name:value` pairs to replace the `name` parameters with their corresponding `value`. For example supplying `--urlParams=version:v1.0` will replace the `version` parameter from our example above with the value "v1.0".

# Dealing with AnyOf, AllOf and OneOf
CATS also supports schemas with `oneOf`, `allOf` and `anyOf` composition. CATS wil consider all possible combinations when creating the fuzzed payloads.

# Limitations

## Inheritance and composition

`allOf` are supported at any object tree level. However, `anyOf` and `oneOf` are supported just at the first level within the object tree model. For example, this is a supported Object composition:

```yaml
Request:
    payload:
      oneOf:
        - $ref: '#/components/schemas/Payload1'
        - $ref: '#/components/schemas/Payload2'
      discriminator:
        propertyName: payloadType
```
However, if `Payload1` or `Payload2` will have an additional compositions, this won't be considered by CATS.

## Additional Parameters

If a response contains a free Map specified using the `additionalParameters` tag CATS will issue a `WARN` level log message as it won't be able to validate that the response matches the schema.


## Regexes within 'pattern'

Cats uses [RgxGen](https://github.com/curious-odd-man/RgxGen) in order to generate Strings based on regexes. This has certain limitations mostly with complex patterns.

# Contributing
Please refer to [CONTRIBUTING.md](CONTRIBUTING.md)
