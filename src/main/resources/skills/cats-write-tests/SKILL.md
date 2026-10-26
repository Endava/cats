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

## 2. Authoring Stateful Functional Flows (`cats run -c <spec> -s <url> flows.yml`)

A functional flow YAML file executes an ordered sequence of HTTP steps (`steps:`), automatically starting from a schema-valid payload and OpenAPI-documented headers generated from the contract, applying step overrides/omissions, extracting variables from responses (`save:`), and evaluating assertions (`expect:`).

### Minimal-by-Default Authoring Rules (Only Specify What Serves a Purpose)
1. **Never repeat OpenAPI-documented headers (`headers:`)** unless the step intentionally tests a specific header value (e.g., re-sending an identical idempotency key across two steps). CATS automatically generates valid values for all headers declared in the OpenAPI contract.
2. **Never write `matchesSchema: true`** — response schema validation is enabled by default (`matchesSchema: true` is implicit). Only set `matchesSchema: false` when intentionally asserting an error response that deviates from the documented schema.
3. **Write expected field values directly under `expect:`** (no `fields:` wrapper needed), and use top-level `status: 204` on steps that only assert the HTTP status code.
4. **Use shorthand `save: [field1, field2]`** when the variable name matches the response JSON field name, or `save: { alias: fieldPath }` (or `$steps.<stepName>.response.<field>`) when renaming.
5. **Only override fields in `body:` and `query:` that matter to the scenario** — with `base: merge` (default), CATS populates all other required schema fields automatically and omits unrequested optional query parameters on `GET`/`DELETE`.
6. **Never assert `response.<requiredField> != null` in `expect.that:`** when the field is already required by the OpenAPI response schema or captured in `save:`.
7. **Always omit recursive self-referencing array/object properties on leaf nodes** — if `cats generate` shows a recursive schema (such as an AST/expression node with `children: [$ref: '#/components/schemas/Expression']`), add `omit: ["expression.children"]` in `correlate:` or on the step so leaf conditions do not carry auto-generated child trees.
8. **Use captured resource IDs (not display names) for cross-resource references** — when one resource references another (e.g., a rule referencing a watchlist via `IN_WATCHLIST`), pass the captured identifier (`${watchlistId}` or `${steps.createWatchlist.response.watchlistId}`) unless the OpenAPI field description explicitly requires a display name.
9. **Order stateful steps deterministically (`Create` $\rightarrow$ `Read/List` $\rightarrow$ `Referential/Negative Checks` $\rightarrow$ `Update/Patch` $\rightarrow$ `Delete Teardown`)** — never delete a resource mid-flow while later `GET`/`PUT`/`PATCH` steps still depend on it; if testing a `409`/`422` referential-integrity error on `DELETE`, run that check while the referencing rule/child resource still actively points to the parent resource ID, then unreference/delete the child before the final parent `DELETE`.

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
  - name: "createPlan"
    description: "Create a metered billing plan and verify its response contract"
    POST: "/billing-plans"
    # Optional oneOf/anyOf variant selection (by schema name or field match)
    variant:
      planType: "METERED"
    # Override only the fields relevant to this test scenario
    body:
      name: "Plan-${envPrefix}-#(alphanumeric(6))"
      planType: "METERED"
      currency: "${defaultCurrency}"
    omit:
      - "flatFeeAmount"
    # Shorthand list saves response.id -> ${id} and response.name -> ${name}
    # (Also supports named map: `save: { createdPlanId: id, etagHeader: headers.ETag }`)
    save:
      - id
      - name
    expect:
      status: [200, 201]
      maxResponseTimeMs: 1000
      planType: "METERED"
      currency: "EUR"
      bodyContains:
        - "Plan-staging-"
      bodyNotContains:
        - "Exception"
      that:
        - "response.name == request.name"
        - "response.unitPrice >= 10"

  - name: "getPlan"
    description: "Fetch the created billing plan and verify cross-step persistence"
    GET: "/billing-plans/${steps.createPlan.response.id}"
    query:
      includeDetails: true
    expect:
      status: 200
      that:
        - "$steps.createPlan.status == 201"
        - "response.id == $steps.createPlan.response.id"
        - "response.name == $prev.response.name"

  - name: "rejectNegativePrice"
    description: "Reject invalid negative unit price on update"
    PUT: "/billing-plans/${id}"
    body:
      name: "${name}"
      planType: "METERED"
      unitPrice: -5
    expect:
      status: [400, 422]
      matchesSchema: false

  - name: "deletePlan"
    description: "Delete the billing plan"
    DELETE: "/billing-plans/${id}"
    status: [200, 204]
```

### Running a Functional Flow
```bash
cats run -c <spec> -s <server_url> flows.yml
```

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

### Supported `when:` and `expect.that:` Operators
- Logical connectives: `&&` (`AND`), `||` (`OR`), `!`
- Comparison operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `contains`, `matches` (regex), `is null`, `is not null`
- Prefixes & functions in `expect.that:` (and `${...}` expressions):
  - `request.<fieldPath>` — reads from the sent JSON request body
  - `response.<fieldPath>` — reads from the received JSON response body
  - `status` / `responseTimeMs` — HTTP status code integer and response latency in ms
  - `size($)` / `size(<fieldPath>)` — number of properties or elements on the root JSON object/array or a nested array
  - `T(...)` — full SpEL boolean expression evaluated against `request` and `response` (e.g., `T(java.time.LocalDate).now().isAfter(T(java.time.LocalDate).parse(expiry.toString()))`)
  - `$steps.<stepName>.response.<fieldPath>` / `$steps.<stepName>.request.<fieldPath>` / `$steps.<stepName>.status` — cross-step & cross-endpoint reference to any earlier step
  - `$prev.response.<fieldPath>` / `$prev.request.<fieldPath>` / `$prev.status` — reference to the immediately preceding step
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

## 5. Reference Data & Headers (`--refData refData.yml`, `--headers headers.yml`)

Both `refData.yml` and `headers.yml` support:
- **`all`** — applied to all paths
- **Wildcard path prefixes (`"/orders*"`)** — applied to all matching sub-paths
- **HTTP method scoping (`"POST /orders"`, `"PUT /orders*"`)** — applied only to the specified HTTP method
- **Dot-notation or `#`-notation for nested fields (`shippingAddress.country` or `shippingAddress#country`)**
- **Dynamic expressions (`#(...)`, `T(...)`, `$$ENV_VAR`)** and **`omit: [...]`** to remove fields from payloads and skip fuzzing them

```yaml
all:
  tenantId: "tenant_default"
  omit:
    - "internalTrace"

"/customers*":
  region: "EU"

"POST /customers/{customerId}/orders":
  customerId: "cust_1001"
  shippingAddress.country: "DE"
  requestId: "#(uuid)"
  omit:
    - "legacyField"
    - "shippingAddress.zip"
```
