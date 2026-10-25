---
name: cats-write-tests
description: "Write and execute stateful multi-step functional API tests (--functionalFlow), cross-field correlation rules (--correlations), and reference data (--refData) with CATS. Invoke when the user wants to write API functional tests, CRUD workflows, business rule validations, or generate sample payloads."
argument-hint: "-c <openapi.yml> -s <server-url>"
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

# CATS — Authoring Stateful Functional Flows, Correlations & Reference Data

Use this skill to generate realistic payloads from an OpenAPI contract and author **stateful multi-step functional flows (`--functionalFlow`)**, **cross-field correlation rules (`--correlations`)**, and **reference data (`--refData`)**.

---

## 1. Inspect Operations & Generate Sample Payloads (`cats generate`)

Before writing a functional flow, inspect the contract's operations and generate schema-valid payloads:
```bash
# List all paths and HTTP methods as JSON
cats list --paths -c <spec> -j

# Generate a schema-valid request payload for a specific path and method
cats generate -c <spec> -p /orders -X POST --pretty

# Generate a payload for a named component schema
cats generate -c <spec> -s OrderCreateRequest --pretty
```

To scaffold starter `headers.yml`, `refData.yml`, and a starter functional flow file from the contract:
```bash
cats init -c <spec> -o .
```

---

## 2. Authoring Stateful Functional Flows (`--functionalFlow flows.yml`)

A functional flow YAML file executes an ordered sequence of HTTP steps (`steps:`), automatically starting from a schema-valid payload generated from the OpenAPI contract, applying step overrides/omissions, extracting variables from responses (`save:`), and evaluating rich assertions (`expect:`).

### Complete `flows.yml` Syntax & Reference

```yaml
# Optional initial variables available as ${varName} across all steps
variables:
  envPrefix: "staging"
  defaultCurrency: "EUR"

# Optional flow-scoped cross-field correlation rules applied to steps
correlate:
  - paths: ["/billing-plans"]
    when: "planType == 'METERED'"
    then:
      set:
        billingCycle: "MONTHLY"
        unitPrice: 15
      omit:
        - "flatFeeAmount"

steps:
  - name: "Create a metered billing plan"
    POST: "/billing-plans"
    # Base payload mode:
    #   FULL (default) - start from a full schema-generated payload
    #   REQUIRED_ONLY  - start from required fields only
    #   EMPTY          - start from an empty body (use only what is in `body:`)
    base: FULL
    # Optional oneOf/anyOf variant selection (by schema name or field match)
    variant:
      planType: "METERED"
    headers:
      X-Idempotency-Key: "#(uuid)"
    # Override specific fields in the generated payload (supports nested paths with # or .)
    body:
      name: "Plan-${envPrefix}-#(alphanumeric(6))"
      planType: "METERED"
      currency: "${defaultCurrency}"
    # Omit specific fields from the generated payload
    omit:
      - "flatFeeAmount"
    # Extract variables from the response (status, headers.<Name>, or JSON path in response body)
    save:
      createdPlanId: "id"
      createdPlanName: "name"
      etagHeader: "headers.ETag"
    # Assertions evaluated against the HTTP response
    expect:
      status: [200, 201]
      matchesSchema: true
      headers:
        Content-Type: "application/json"
      fields:
        planType: "METERED"
        currency: "EUR"
      bodyContains:
        - "Plan-staging-"
      bodyNotContains:
        - "Exception"
      where:
        - "response.id != null"
        - "response.name == request.name"
        - "response.unitPrice >= 10"

  - name: "Fetch the created billing plan and verify persistence"
    GET: "/billing-plans/${createdPlanId}"
    path:
      planId: "${createdPlanId}"
    query:
      includeDetails: true
    expect:
      status: 200
      where:
        - "response.id == '${createdPlanId}'"
        - "response.name == '${createdPlanName}'"

  - name: "Reject invalid negative unit price on update"
    PUT: "/billing-plans/${createdPlanId}"
    path:
      planId: "${createdPlanId}"
    body:
      name: "${createdPlanName}"
      planType: "METERED"
      unitPrice: -5
    expect:
      status: [400, 422]
      matchesSchema: false

  - name: "Delete the billing plan"
    DELETE: "/billing-plans/${createdPlanId}"
    path:
      planId: "${createdPlanId}"
    expect:
      status: [200, 204]
```

### Running a Functional Flow
```bash
cats run -c <spec> -s <server_url> --functionalFlow flows.yml
```
*(You can also validate a flow offline in dry-run mode with `cats run -c <spec> -s http://localhost:8080 --functionalFlow flows.yml --dryRun`.)*

---

## 3. Authoring Cross-Field Correlation Rules (`--correlations correlations.yml`)

When an API enforces conditional field dependencies (e.g., when a discriminator or enum field has a specific value, certain sibling fields become required or forbidden), define them in `correlations.yml` so **both** functional flows and all built-in CATS fuzzers satisfy the invariant:

```yaml
correlations:
  - paths:
      - "/billing-plans"
      - "/billing-plans/{planId}"
    when: "planType == 'RECURRING'"
    then:
      set:
        billingSchedule: "MONTHLY"
        gracePeriodDays: 7
      omit:
        - "oneTimeSetupFee"

  - paths:
      - "/billing-plans"
    when: "planType == 'ONE_TIME'"
    then:
      set:
        oneTimeSetupFee: 99
      omit:
        - "billingSchedule"
        - "gracePeriodDays"
```

### Supported `when:` and `expect.where:` Operators
- Logical connectives: `&&` (`AND`), `||` (`OR`), `!`
- Comparison operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `contains`, `matches` (regex), `in ['A', 'B']`
- Prefixes in `expect.where:`:
  - `request.<fieldPath>` — reads from the sent JSON request body
  - `response.<fieldPath>` — reads from the received JSON response body
  - `status` — HTTP status code integer (e.g., `status == 201`)
  - `${varName}` — variable extracted by an earlier step's `save:` block

---

## 4. Built-In Dynamic Shorthand Functions (`#(...)` & `$$ENV`)

Anywhere in `flows.yml`, `correlations.yml`, `refData.yml`, or `headers.yml`, you can use:
- **`${varName}`** — value extracted by a prior step (`save:`) or defined in `variables:` / `--authConfig`
- **`$$ENV_VAR`** — environment variable read from the process environment (or `--envFile`) and automatically masked in reports
- **`#(uuid)`** — deterministic/seeded RFC 4122 UUID v4
- **`#(ulid)`** — 26-char lexicographically sortable ULID
- **`#(email)`** — random valid `@cats.io` email address
- **`#(alphanumeric(N))`** / **`#(alpha(N))`** / **`#(numeric(N))`** — random string of length `N`
- **`#(randomInt(min,max))`** — random integer in `[min, max]`
- **`#(pick(VAL_1,VAL_2,VAL_3))`** — picks one value from the list
- **`#(today)`** / **`#(todayPlus(days))`** / **`#(todayMinus(days))`** — ISO-8601 `YYYY-MM-DD` date
- **`#(now)`** / **`#(nowPlusDays(days))`** / **`#(nowMinusDays(days))`** / **`#(nowPlusHours(hours))`** — ISO-8601 UTC timestamp
- **`#(sha256(value))`** / **`#(base64(value))`** / **`#(urlEncode(value))`** — encoding helpers

---

## 5. Reference Data (`--refData refData.yml`)

Use `refData.yml` to pin fixed field values or remove fields (`cats_remove_field`) per path or across `all` paths:
```yaml
all:
  tenantId: "tenant_default"

"/customers/{customerId}/orders":
  customerId: "cust_1001"
  shippingAddress#country: "DE"
  legacyField: "cats_remove_field"
```
