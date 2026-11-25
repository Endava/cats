---
sidebar_position: 6
description: How CATS can help to automatically create reference data
---

# Creating Ref Data Files Dynamically

You can use a [`FunctionalFlowFuzzer`](../fuzzers/special-fuzzers/functional-fuzzer) (`steps:`) workflow—or `cats triage --export-refdata`—to generate `--refData` files dynamically instead of hardcoding resource identifiers.

## Option 1: Export `--refData` from a functional flow (`--export-refdata`)

Capture identifiers in a `steps:` flow's `save:` block, reference them in downstream steps, and pass `--export-refdata` when running `cats run`:

```bash
cats run functionalFlow.yml -c contract.yml -s http://localhost:8080 --export-refdata refData.yml
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
      petId: "pet.id"
    expect:
      status: [200, 201]

  - name: "getPetType"
    GET: "/pet-type"
    query:
      id: "${petId}"
    expect:
      status: 200
```

After `cats run` finishes, CATS writes `refData.yml` with the resolved runtime values for every path and field where a saved variable was consumed:

```yaml
/pet-type:
  id: 123
```

## Option 2: Mine `--refData` from an existing report with `cats triage`

If you have already run CATS and want to extract working path parameters and resource IDs observed in `2XX` responses across the report:

```bash
cats triage -r cats-report --export-refdata refData.yml
```
