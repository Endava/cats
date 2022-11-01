
### BooleanFieldsFuzzer
This `Fuzzer` applies only to Boolean fields. It will try to send invalid boolean values and expects a `4XX` response code.

### DecimalFieldsLeftBoundaryFuzzer and DecimalFieldsRightBoundaryFuzzer
This `Fuzzer` will run boundary tests for fields marked as `Number`, including `float` and `double` formats.  It will use the `minimum` property to generate a left boundary value or `maximum` for the right boundary one.
If any of these values are not set, it will use `Long.MIN_VALUE` and `Long.MAX_VALUE`. It expects a `4XX` response code.

### IntegerFieldsLeftBoundaryFuzzer and IntegerFieldsRightBoundaryFuzzer
This `Fuzzer` is similar to the `Decimal Fuzzers`, but for `Integer` fields, both `int32` and `int64` formats.

### ExtremeNegativeValueXXXFieldsFuzzer and ExtremePositiveValueXXXFuzzer
These `Fuzzers` apply for `Decimal` and `Integer` fields. They will send either an extremely low negative value or an extremely high positive value as follows:
- for `Decimal` fields: `-999999999999999999999999999999999999999999.99999999999`when no format is specified, and `-Float.MAX_VALUE` for `float` and `-Double.MAX_VALUE` for `double`
- for `Integer` fields: `Long.MIN_VALUE` when no format is specified or `int32`and `2 * Long.MIN_VALE` for `int64`

These `Fuzzers` expect a `4XX` response code.

### RemoveFieldsFuzzer
This `Fuzzer` will remove fields from the requests based on a supplied strategy. It will create subsets of all the fields and subfields within the request schema. Based on these subsets, it will:
- iterate through them one by one
- remove the fields present in the current subset from a full service payload
- send the modified request to the server

These subsets can be generated using the following strategies (supplied through the `--fieldsFuzzingStrategy` option):

#### POWERSET
This is the most time-consuming strategy. This will create all possible subsets of the request fields (including subfields). If the request contains a lot of fields, this strategy might not be the right choice as the total number of possibilities is `2^n`, where `n` is the number of fields.

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

#### ONEBYONE
This is the faster strategy and also the **default** one. This will iterate though each request field (including subfields) and create a single element set from it. The `FieldFuzzer` will iterate though the resulting sets and remove those fields (and subfields) from the request i.e. one field at a time. All the other headers and fields will remain unchanged.

If we take the example above again, the resulting sets produced by this strategy will be as follows:

`{address#phone}, {address#postcode}, {address#street}, {name}`

#### SIZE
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

### StringFieldsRightBoundaryFuzzer
The max length of a String supported by the JVM APIs is `Integer.MAX_VALUE` which is `2^31-1`.

Based on this constraint, this Fuzzer will send String values whose length are bigger than the defined `maxLength` property:
- if the `maxLength` is equal to `2^31-1`, the Fuzzer won't run as it cannot create Strings larger than this value
- if the `maxLength` is between `2^31-1 - 10` and `2^31-1 - 2`, the Fuzzer will generate Strings with a length of `2^31-1 - 2`
- if the `maxLength` is less than `2^31-1 - 10`, the Fuzzer will generate Strings with a length of `maxLength + 10`
- if no `maxLength` is defined, the Fuzzer will generate a string of `10 000` characters

**Please note that when having string properties with such high `maxLength` the probability of getting `OutOfMemoryErrors` is quite high.
There are very few cases when this is actually needed as it will also take a long time to send such a huge payloads to the service.
Please consider setting reasonable `maxLength` values which make sense in your business context.
Setting reasonable boundaries for your inputs is also a good practice from a security perspective and will prevent your service from crashing when dealing with large inputs.**

### StringFormatAlmostValidValuesFuzzer
OpenAPI offers the option to specify `formats` for each `string` field. This gives hints to the client on what type of data is expected by the API.
This `Fuzzer` has a predefined list of `formats`. For all `strings` matching any of the predefined `formats` it will send values which are 'almost valid' for that particular format. For example:
- if the `format` is `password` it will send the `string` `bgZD89DEkl` which is an almost valid strong password (except that it doesn't contain special characters).
- if the `format` is `email` it will send the `string` `email@bubu.` which is an almost valid email (except it doesn't contain the domain extension).
- and so on.
  The following formats are supported: `byte, date, date-time, hostname, ipv4, ipv6, ip, password, uri, url, uuid`
  The `Fuzzer` expects a `4XX` response code.

### StringFormatTotallyWrongValuesFuzzer
This behaves in the same way as the previous `Fuzzer`, but the values sent for each format are totally invalid (like `aaa` for `email` for example).

### NewFieldsFuzzer
This `Fuzzer` will inject new fields inside the body of the requests. The new field is called `fuzzyField`. The `Fuzzers` will behave as follows:
- Normal behaviour is for the service to return a `4XX` code for `POST`, `PUT` and `PATCH` and a `2XX` code for `GET`. If the code is documented, this will be reported as an `INFO` message, otherwise as a `WARN` message.
- If the code responds with a `2XX` or `4XX` code, depending on the previous point, this is considered abnormal behaviour and will reported as an `ERROR` message.
- Any other case is reported as an `ERROR` message.

### StringsInNumericFieldsFuzzer
This `Fuzzer` will send the `fuzz` string in every numeric fields and expect all requests to fail with `4XX`.

### LeadingWhitespacesInFieldsTrimValidateFuzzer, TrailingWhitespacesInFieldsTrimValidateFuzzer and OnlyWhitespacesInFieldsTrimValidateFuzzer
This `Fuzzers` will prefix or trail each field with Unicode whitespaces and invisible chars.
The expected result is that the service will sanitize these values and a `2XX` response code is received. These `Fuzzers` will fuzz all fields types except for discriminator fields.
It's critical for APIs to sanitize input values as they will eventually lead to unexpected behaviour.

Please note that CATS tests iteratively for **18 whitespace characters**. This means that for **each field** within the requests CATS will run **18 tests**.
This is why the number of tests (and time to run) CATS will increase significantly depending on the number of endpoints and request fields.
Please check the [Slicing Strategies](#slicing-strategies-for-running-cats) section on recommendations on how to split Fuzzers in batches so that you get optimal running times and reporting.

### LeadingControlCharsInFieldsTrimValidateFuzzer, TrailingControlCharsInFieldsTrimValidateFuzzer and OnlyControlCharsInFieldsTrimValidateFuzzer
This `Fuzzers` will prefix or trail each field with Unicode control chars.
The expected result is that the service will sanitize these values and a `2XX` response code is received. These `Fuzzers` will fuzz all fields types except for discriminator fields.
It's critical for APIs to sanitize input values as they will eventually lead to unexpected behaviour.

Please note that CATS tests iteratively for **63 control characters**. This means that for **each field** within the requests CATS will run **63 tests**.
This is why the number of tests (and time to run) CATS will increase significantly depending on the number of endpoints and request fields.
Please check the [Slicing Strategies](#slicing-strategies-for-running-cats) section on recommendations on how to split Fuzzers in batches so that you get optimal running times and reporting.

### WithinControlCharsInFieldsSanitizeValidateFuzzer
This `Fuzzers` will insert Unicode control chars within each field.

Depending on the `--sanitizationStrategy` argument, this `Fuzzer` will expect:
- `2XX` if `--sanitizationStrategy=sanitizeAndValidate`. This is also the default value (i.e. when not specifying and explicit strategy).
- `2XX` or `4XX` depending on the specific regex set for the fuzzed field when `--sanitizationStrategy=validateAndSanitize`.

These `Fuzzers` will fuzz only `String` fields.
It's critical for APIs to sanitize input values as they will eventually lead to unexpected behaviour.

Please note that CATS tests iteratively for **63 control characters**. This means that for **each field** within the requests CATS will run **63 tests**.
This is why the number of tests (and time to run) CATS will increase significantly depending on the number of endpoints and request fields.
Please check the [Slicing Strategies](#slicing-strategies-for-running-cats) section on recommendations on how to split Fuzzers in batches so that you get optimal running times and reporting.
