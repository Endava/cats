--- 
hide_table_of_contents: true
---

# Remove Fields

| Item                                                                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|:--------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full Fuzzer Name**                                                | RemoveFieldsFuzzer                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **Log Key**                                                         | **RF**                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **Description**                                                     | This fuzzer will remove fields in different combinations. The number of all possible combinations is driven by the `--fieldsFuzzingStrategy` argument. The expectation is that if required fields are removed the APIs will reject the request as invalid.                                                                                                                                                                                  |
| **Enabled by default?**                                             | Yes                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Target field types**                                              | All                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Expected result when fuzzed field is required**                   | `4XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed field is optional**                   | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Expected result when fuzzed value is matching not field pattern** | `2XX`                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Fuzzing logic**                                                   | Iteratively **removes** fields in different combinations                                                                                                                                                                                                                                                                                                                                                                                    |
| **Conditions when this fuzzer will be skipped**                     | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **HTTP methods that will be skipped**                               | None                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Reporting**                                                       | Reports `error` if: *1.* response code is `404`; *2.* response code is documented, but not expected; *3.* any unexpected exception. <br/><br/> Reports `warn` if: *1.* response code is expected and documented, but not matches response schema; *2.* response code is expected, but not documented; *3.* response code is `501`. <br/><br/> Reports `success` if: *1.* response code is expected, documented and matches response schema. | 

## Details on How Combinations Are Calculated

This Fuzzer will remove fields from the requests based on a supplied strategy. It will create subsets of all the fields and subfields within the request schema. Based on these subsets, it will:
- iterate through them one by one
- remove the fields present in the current subset from a full request payload
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

All the fields, including subfields will look like this: `{name, address#phone, address#postcode, address#street}`. Using the `POWERSET` strategy there are 16 possible subsets. 
The Fuzzer will iterate through each set and remove those fields (and subfields) from the request. All the other headers and request fields will remain unchanged.

#### ONEBYONE
This is the faster strategy and also the **default** one. This will iterate though each request field (including subfields) and create a single element set from it. 
The Fuzzer will iterate though the resulting sets and remove those fields (and subfields) from the request i.e. one field at a time. All the other headers and fields will remain unchanged.

If we take the example above again, the resulting sets produced by this strategy will be as follows:

`{address#phone}, {address#postcode}, {address#street}, {name}`

#### SIZE
This is a mixed strategy. It applies principles from the `POWERSET` strategy, but will remove a maximum number of fields (and subfields) supplied though the `--maxFieldsToRemove` option. 
This means that will generate subsets of fields (and subfields) having size `n - maxFieldsToRemove` or greater, where `n` is the total number of fields and subfields.

If `--maxFieldsToRemove` for the example above is `2`, the resulting sets produced by this strategy will be as follows:

```json lines
{address#phone, address#postcode, address#street}, {name, address#postcode, address#street}, {name, address#phone, address#street}, {name, address#phone, address#postcode}, {name, address#street}, {name, address#postalcode}, {name, address#phone}, {address#phone, address#postalcode}, {address#phone, address#street}, {address#postalcode, address#street}
```
