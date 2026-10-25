---
name: cats-analyze-api
description: "Validate, lint, and analyze OpenAPI/Swagger specifications with CATS without needing a running server. Computes the 0-100 Fuzzability Score, Producer-to-Consumer dependency DAG, orphan path parameters, reusability metrics, and 31 contract linting checks."
argument-hint: "-c <openapi.yml>"
allowed-tools:
  - read
  - edit
  - grep
  - glob
  - exec
triggers:
  - user
  - model
---

# CATS — OpenAPI Contract Validation, Fuzzability Analysis & Linting

Use this skill whenever the user wants to validate an OpenAPI/Swagger contract, audit API design quality, understand endpoint dependencies, or improve a spec's fuzzability — **no running server is required**.

---

## 1. Validate Contract Structure (`cats validate`)

Check whether the OpenAPI 3.0 / 3.1 / Swagger 2.0 specification parses cleanly:
```bash
cats validate -c <spec> -j
```
- Exit code `0`: Valid contract.
- Exit code `!= 0`: Inspect the reported parser errors (unresolved `$ref`, invalid YAML/JSON syntax, missing required OpenAPI root fields) and fix them first.

---

## 2. Compute Fuzzability Blueprint & Dependency DAG (`cats analyze`)

Run `cats analyze` to inspect how well the contract supports automated stateful testing:
```bash
cats analyze -c <spec> -j
```

Key fields in the JSON output (`BlueprintReport`):
- **`fuzzabilityScore` (0–100)** & **`grade` (`A`–`F`)**: Overall readiness for automated fuzzing.
- **`breakdown`**:
  - `schemaConstraintCoverage` — percentage of properties with explicit constraints (`minLength`, `maxLength`, `minimum`, `maximum`, `pattern`, `format`, `enum`).
  - `exampleCoverage` — percentage of operations/schemas providing concrete examples.
  - `producerConsumerCoverage` — percentage of parameterized paths (`/items/{id}`) backed by a matching `POST` producer.
  - `errorCodeDocumentation` — percentage of operations documenting `4XX`/`5XX` responses.
  - `discriminatorCompleteness` — completeness of `oneOf`/`anyOf` discriminator mappings.
- **`chains` (`ProducerConsumerChain[]`)**: Ordered list of `producer` (`POST /resources`) $\rightarrow$ `consumers` (`GET /resources/{id}`, `PUT /resources/{id}`, `DELETE /resources/{id}`) linked by `sharedIdentifier`. Use these chains when designing functional flows.
- **`orphanPathParameters`**: Path variables that have no `POST` producer in the contract and therefore require `--refData` or `--urlParams`.
- **`mermaidDiagram`**: Ready-to-render Mermaid flowchart of operation dependencies (or pass `--mermaid` for console output).

---

## 3. Compute Contract Statistics (`cats stats`)

Get high-level counts and schema reusability metrics:
```bash
cats stats -c <spec> -j
```
Reports total paths, operations, schemas, headers, parameters, response codes, content types, and schema reuse ratios.

---

## 4. Audit Best Practices with 31 Contract Linters (`cats lint`)

Run all contract linters (no network calls made):
```bash
cats lint -c <spec> -j -o cats-lint-report
```

Enforce explicit naming conventions when requested:
```bash
cats lint -c <spec> \
  --pathNaming KEBAB \
  --pathVariablesNaming CAMEL \
  --queryParamsNaming CAMEL \
  --jsonPropertiesNaming CAMEL \
  --jsonObjectsNaming PASCAL \
  --headersNaming HTTP_HEADER \
  --enumsNaming UPPER_UNDERSCORE
```
*(Supported naming styles: `CAMEL`, `PASCAL`, `SNAKE`, `KEBAB`, `HTTP_HEADER`, `UPPER_UNDERSCORE`, `LOWER_UNDERSCORE`.)*

Useful discovery commands for linters:
```bash
cats list -l -j
cats explain -t LINTER <LinterName>
```

---

## Agent Remediation Loop for OpenAPI Specs

When asked to improve an OpenAPI specification:
1. Run `cats validate -c <spec> -j`, `cats analyze -c <spec> -j`, and `cats lint -c <spec> -j`.
2. Fix structural and high-impact gaps in the spec:
   - Add missing `operationId`, `summary`, `description`, and `tags`.
   - Add `minimum`/`maximum`, `minLength`/`maxLength`, `format`, or `enum` constraints to unconstrained `string`/`integer`/`number` properties.
   - Document `400`, `401`, `403`, `404`, and `500` responses alongside `2XX`.
   - Ensure `POST` responses return the created resource's primary identifier (`id`) so CATS can automatically correlate it into `{id}` path parameters.
3. Re-run `cats analyze -c <spec> -j` and `cats lint -c <spec> -j` to verify the fuzzability score increased and linter errors dropped.
