---
sidebar_position: 9
description: Supplying context to CATS
---

# Reference Data File
Most of the time some fields need to contain relevant data in order for a request to succeed. CATS considers this type of data `reference data`. 
You can provide such values using the `--refData` argument. 
The reference data file is a YAML file that specifies fixed values for different request paths. The file structure is as follows:

```yaml
path1:
  prop1: value1
  prop2: value2
path2:
  prop21: value21
```

:::note
Reference data values have priority over values supplied by the Fuzzers.
:::

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

and the following reference data file:


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

:::info
Although reference data values takes precedence over fuzzed values, CATS will try to merge fuzzed values with the ref data values when possible. 
This means that even if fields will get replaced with their reference data value, they will still get properly fuzzed, thus letting CATS do its magic as expected.
:::

The file uses [Json path](https://github.com/json-path/JsonPath) syntax for properties names; you can supply fully qualified properties using `#` as separator (instead of `.`).

You can use environment (system) variables in a ref data file using: `$$VARIABLE_NAME`.

:::caution
Notice double `$$` when supplying environment variables.
:::

## Setting additionalProperties
As additional properties are maps i.e. they don't actually have a schema, CATS cannot generate valid values. If the elements within such a data structure are essential for a request,
you can supply them via the `--refData` file using the `additionalProperties` element:

```yaml
/path/0.1/auth:
    address#street: "My Street"
    name: "John"
    additionalProperties:
      topElement: metadata
      mapValues:
        test: "value1"
        anotherTest: "value2"
```

The `additionalProperties` element must contain the actual key-value pairs to be sent within the requests and also a `topElement` if needed (not mandatory). `topElement` will be the name of the map.

The above example will output the following json (considering also the above examples):

```json
{
    "address": {
        "phone": "123",
        "postCode": "408",
        "street": "My Street"    
    },
    "name": "John",
    "metadata": {
        "test": "value1",
        "anotherTest": "value2"
    }   
}
```

## Sending Reference Data for ALL Paths
You can also have the ability to send the same reference data for ALL paths (just like you do with the headers). You can achieve this by using `all` as a key in the `--refData` file:

```yaml
all:
  address#zip: 123
```
This will try to replace `address#zip` in **all** requests (if the field is present).

## Removing Fields
There are (rare) cases when some fields may not make sense together. Something like: if you send `firstName` and `lastName`, you are not allowed to also send `name`.
As OpenAPI does not have the capability to send request fields which are dependent on each other, you can use the `refData` file to instruct CATS to remove fields before sending a request to the service.
You can achieve this by using the `cats_remove_field` as a value for the fields you want to remove. For the above case the `--refData` field will look as follows:

```yaml
all:
  name: "cats_remove_field"
```

## Reference Data Reserved Keywords
The following keywords are reserved in a reference data file: `all`, `additionalProperties`, `topElement`, `mapValues` and `cats_remove_field`.
