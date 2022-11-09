---
sidebar_position: 6
description: How CATS can help to automatically create reference data
---

# Creating Ref Data Files
You can leverage the fact that the `FunctionalFuzzer` can run functional flows in order to create dynamic `--refData` files which won't need manual setting the reference data values.
The `--refData` file must be created with variables `${variable}` instead of fixed values and those variables must be output variables in the `functionalFuzzer.yml` file.
In order for the `FunctionalFuzzer` to properly replace the variables names with their values you must supply the `--refData` file as an argument when the `FunctionalFuzzer` runs.

```bash
cats run functionalFuzzer.yml -c contract.yml -s http://localhost:8080 --refData=refData.yml
```

The `functionalFuzzer.yml` file:

```yaml
/pet:
  test_1:
    description: Create a Pet
    httpMethod: POST
    name: "My Pet"
    expectedResponseCode: 200
    output:
      petId: pet#id
```

The `refData.yml` file:

```yaml
/pet-type:
  id: ${petId}
```

After CATS runs using the above command, you will get a `refData_replace.yml` file where the `id` will get the value returned into the `petId` variable.

The `refData_replaced.yml`:

```yaml
/pet-type:
  id: 123
```

You can now use the `refData_replaced.yml` as a `--refData` file for running CATS with the rest of the Fuzzers.
