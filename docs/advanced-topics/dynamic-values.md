---
sidebar_position: 5
---

# Dynamic Values
The following configuration files: `SecurityFuzzer` file, `FunctionalFuzzer` file and `--refData` support setting dynamic values.
For now **the support only exists** for `java.time.*` and `org.apache.commons.lang3.*`, but more types will be added in the future.

Let's suppose you have a date/date-time field, and you want to set it to 10 days from now. You can do this by setting this as a value `T(java.time.OffsetDateTime).now().plusDays(10)`.
This will return an ISO compliant time in UTC format.

A `FunctionalFuzzer` using this can look like:
```yaml
/path:
    testNumber:
        description: Short description of the test
        prop: value
        prop#subprop: "T(java.time.OffsetDateTime).now().plusDays(10)"
        prop7:
          - value1
          - value2
          - value3
        oneOfSelection:
          element#type: "Value"
        expectedResponseCode: HTTP_CODE
        httpMethod: HTTP_NETHOD
```

You can also check the responses using a similar syntax and also take into consideration the actual values returned within the response. 
This is a syntax than can test if a returned date is after the current date:`T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))`. 
This will check if the `expiry` field returned within the json response, parsed as date, is after the current date:

```yaml
/path:
    testNumber:
        description: Short description of the test
        prop: value
        prop#subprop: "T(java.time.OffsetDateTime).now().plusDays(10)"
        prop7:
          - value1
          - value2
          - value3
        oneOfSelection:
          element#type: "Value"
        expectedResponseCode: HTTP_CODE
        httpMethod: HTTP_NETHOD
        verify:
          checkBoolean: T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))
```

:::info
Notice the keyword `checkBoolean` which will test if the expression is `true`. This is very useful when doing assertions on response data when running functional tests.
:::

The syntax of dynamically setting dates is compliant with the [Spring Expression Language](https://docs.spring.io/spring-framework/docs/3.0.x/reference/expressions.html) specs.
