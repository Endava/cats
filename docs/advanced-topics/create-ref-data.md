---
sidebar_position: 6
description: How CATS can help to automatically create reference data
---

# Creating Ref Data Files Dynamically

You can use a [`FunctionalFlowFuzzer`](../fuzzers/special-fuzzers/functional-fuzzer) (`steps:`) workflow—or `cats triage --export-refdata`—to generate `--refData` files dynamically instead of hardcoding resource identifiers.

## Option 1: Populate a `--refData` template from a functional flow

Create a `refData.yml` template containing `${variable}` placeholders that match variables captured in a `steps:` flow's `save:` block, and pass `--refData=refData.yml` when running `cats run`:

```bash
cats run functionalFlow.yml -c contract.yml -s http://localhost:8080 --refData=refData.yml
```

The `functionalFlow.yml` file:

```yaml
steps:
  - name: "createPet"
    description: "Create a Pet and capture its ID"
    POST: "/pet"
    body:
      name: "My Pet"
    save:
      - petId: "pet.id"
    expect:
      status: [200, 201]
```

The `refData.yml` template:

```yaml
/pet-type:
  id: ${petId}
```

After `cats run` finishes, CATS writes `refData_replaced.yml` with the resolved runtime value:

```yaml
/pet-type:
  id: 123
```

You can also pass `--createRefData` without an input `refData.yml` template (`cats run functionalFlow.yml -c contract.yml -s http://localhost:8080 --createRefData`), and CATS will record every path and field where a saved variable was consumed into `refData_custom.yml`.

## Option 2: Mine `--refData` from an existing report with `cats triage`

If you have already run CATS and want to extract working path parameters and resource IDs observed in `2XX` responses across the report:

```bash
cats triage -r cats-report --export-refdata refData.yml
```
