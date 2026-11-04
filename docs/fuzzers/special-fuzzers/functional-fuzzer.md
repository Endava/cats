---
sidebar_position: 1
description: Write stateful multi-step functional API tests and cross-field correlations without coding
---

# Functional Flow Fuzzer

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `FunctionalFlowFuzzer` |
| **Log Key** | N/A; step names and descriptions from the flow file identify executions. |
| **Description** | Executes sequential, stateful multi-step functional API workflows (`steps:`) with cross-step variable correlation, cross-field rules (`correlate:`), and rich response assertions (`expect:`). |
| **Enabled by default?** | No. Enable it with `cats run -c <contract> -s <server> <flow-file.yml>`. |
| **Target fields** | Request body fields, path parameters, query parameters, and HTTP headers configured in each step. |
| **Expected result** | The `status` and `expect` assertions configured on each step (with OpenAPI response schema validation enabled by default). |
| **Fuzzing logic** | Starts from a schema-valid payload and OpenAPI-documented headers generated from the contract, applies step `body`/`query`/`path`/`headers` overrides, `omit` lists, and `correlate` rules, executes steps sequentially, captures response data (`save`), and evaluates `expect` assertions. |
| **Conditions when this fuzzer will be skipped** | When no functional flow file is supplied or a step's method/path does not match any operation in the OpenAPI contract. |
| **HTTP methods that will be skipped** | Any HTTP method not configured in the flow steps. |
| **Reporting** | Reports `error` on cross-family status mismatches (e.g. `4XX`/`5XX` when `2XX` is expected) or failed `expect` assertions, `warn` on same-family status code mismatches or schema mismatches, and `info`/`success` when all expectations pass. |

You can leverage CATS's self-healing payload generation to write end-to-end functional API workflows in YAML without writing code, executed via `cats run`:

```shell
cats run -c openapi.yml -s http://localhost:8080 functionalFlow.yml
```

Because CATS automatically generates schema-valid request payloads and OpenAPI-documented headers from your contract, each step only needs to specify the fields relevant to your business scenario.

---

## Sequential `steps:` Syntax

A functional flow file can define optional top-level `variables:`, optional flow-scoped `correlate:` rules, and an ordered list of HTTP `steps:`:

```yaml
variables:
  defaultCountry: "RO"

correlate:
  - paths:
      - "/go"
    when: "pet.type == 'Dog'"
    then:
      omit:
        - "catOnlyAttribute"

steps:
  - name: "createDogGo"
    description: "Create a Dog journey with custom metadata and capture the generated homeId"
    POST: "/go"
    variant:
      pet.type: "Dog"
    body:
      home.country: "${defaultCountry}"
      metadata:
        test: "bubu"
        anotherTest: "juju"
    omit:
      - "legacyField"
    save:
      - id
      - homeId: "home.id"
    expect:
      status: [200, 201]
      maxResponseTimeMs: 1000
      pet.name: "Baby"
      pet.id: "[0-9]+"
      that:
        - "response.home.country == request.home.country"

  - name: "getCreatedGo"
    description: "Retrieve the created journey and verify cross-step persistence"
    GET: "/go/${id}"
    delayMs: 50
    poll:
      maxAttempts: 3
      intervalMs: 100
    expect:
      status: 200
      that:
        - "response.id == $steps.createDogGo.response.id"
        - "response.home.id == $prev.response.home.id"

  - name: "movePetToHome"
    description: "Move the pet using the saved homeId from the first step"
    POST: "/pets/${id}/move"
    body:
      homeId: "${homeId}"
      referenceCode: "#(alphanumeric(8))"
    expect:
      status: 200
      that:
        - "$steps.createDogGo.status == 200 || $steps.createDogGo.status == 201"
```

### Step Reference

- **HTTP method and target path**:
  - Shorthand method key: `POST: "/go"`, `GET: "/go/${id}"`, or `PATCH: "/risk-rules/${ruleId}?dryRun=false"` (inline path parameters and query strings are extracted automatically).
  - Explicit keys: `method: POST` and `path: "/go"` (or `paths: ["/go", "/go/v2"]`).
- **`name`** (or `id`) and **`description`**:
  - `name` uniquely identifies the step for cross-step `$steps.<name>.*` references.
  - `description` sets the scenario text displayed in logs and reports (falls back to `name` if omitted).
- **`base`**: Controls how CATS initializes the request payload before applying `body:` overrides:
  - `MERGE` (default): Starts from a full schema-valid happy-path payload and merges `body:` overrides. On `GET` and `DELETE`, optional query parameters not explicitly listed in `query:` (or inline in the URL) are omitted.
  - `ONLY_REQUIRED`: Starts from a payload containing only fields marked `required` in the OpenAPI schema.
  - `EMPTY`: Starts from an empty `{}` payload.
- **`body`**: Map, list, or scalar overriding the request body. Nested maps merge into existing JSON objects, and dot-notation (`home.country`) or `#`-notation (`home#country`) can target nested fields directly. All YAML types (`integer`, `number`, `boolean`, lists, nested maps) and explicit `null` values are preserved.
- **`path`**, **`query`**, **`headers`**: Maps of path parameters, query parameters, and HTTP headers for the step. OpenAPI-documented headers are generated automatically and only need to be listed in `headers:` when overriding a specific value.
- **`omit`**: List of field paths to remove from the generated request payload.
- **`variant`**: Selects a specific `oneOf`/`anyOf` schema branch either by schema name suffix (`variant: "DogRequest"`) or by matching discriminator/property values (`variant: { pet.type: "Dog" }`).
- **`correlate`**: Optional step-scoped cross-field correlation rule (single object `{ when: "...", set: {...}, omit: [...] }` or list of rules) applied before sending the step's request.
- **`delayMs`**: Optional initial delay in milliseconds before sending the step's request.
- **`poll`**: Optional polling configuration (`{ maxAttempts: 5, intervalMs: 200 }`) for eventual-consistency or asynchronous state transitions. CATS retries the step up to `maxAttempts` times, waiting `intervalMs` between attempts, until `status` and `expect` assertions pass, and reports only the final attempt.
- **`repeat`**: Optional integer count (`repeat: N`, default `1`). Executes `N - 1` setup requests (updating saved variables on each response) and evaluates `expect:` assertions on the `N`th request—useful for rate-limit, quota exhaustion, or replay/conflict checks.
- **`forEach`**: Optional list of variable maps (`- { currency: "EUR", minPrice: 10 }`) that expands the step into one execution per row, binding each map's keys as `${varName}` while preserving their native YAML types (`integer`, `number`, `boolean`, `null`, lists, or maps).

```yaml
  - name: "createPlansByCurrency"
    description: "Data-driven step executed once per row in forEach"
    POST: "/billing-plans"
    forEach:
      - { currency: "EUR", minPrice: 10, promoCode: null }
      - { currency: "USD", minPrice: 12, promoCode: "WELCOME" }
    body:
      currency: "${currency}"
      unitPrice: "${minPrice}"
      promoCode: "${promoCode}"
    expect:
      status: [200, 201]
      currency: "${currency}"

  - name: "exhaustOneOffActivation"
    description: "Execute N-1 setup calls and assert expect on the Nth call"
    POST: "/billing-plans/${id}/activate"
    repeat: 2
    expect:
      status: [400, 409, 422, 429]
```

---

## Saving Variables (`save:`) and Cross-Step References (`$steps`, `$prev`)

You can pass values from earlier steps to later steps in two ways:

### 1. Explicit `save:` list or map
`save:` accepts a shorthand list of field names, named alias mappings, or a mix of both:

```yaml
save:
  - id                             # saves response.id -> ${id}
  - homeId: "home.id"              # saves response.home.id -> ${homeId}
  - accessToken: "header#X-Token"  # saves response header X-Token -> ${accessToken}
  - rawScalar: "$body"             # saves root scalar response body -> ${rawScalar}
  - sentName: "$request.name"      # saves sent request field -> ${sentName}
```

Supported selectors in `save:`:
- `field.path` or `$response.field.path`: Extracts a JSON property from the response body (if the root response is a JSON array, CATS automatically unwraps the first element when resolving field paths).
- `$`, `$body`, or `$response.body`: Captures a root scalar response body.
- `header#Header-Name` or `header.Header-Name`: Captures a response header.
- `$request.field.path` or `$path.paramName`: Captures a value from the sent request body or path parameters.

Saved variables can be referenced as `${variableName}` in subsequent step paths, `body:`, `query:`, `headers:`, `expect:`, or in external `--headers`, `--queryParams`, and `--refData` files.

### 2. Direct `$steps` and `$prev` references
Every step automatically records its request, response, and status code, so later steps can reference them without declaring a `save:` entry:
- `$steps.<stepName>.response.<fieldPath>` / `${steps.<stepName>.response.<fieldPath>}`
- `$steps.<stepName>.request.<fieldPath>`
- `$steps.<stepName>.status`
- `$prev.response.<fieldPath>`, `$prev.request.<fieldPath>`, `$prev.status` (references the immediately preceding step)

---

## Response Assertions (`status` and `expect:`)

By default, every step expects `2XX` and validates the response body against the OpenAPI response schema (`matchesSchema: true`).

For steps that only check the status code, use top-level `status:`:

```yaml
- name: "deletePlan"
  DELETE: "/billing-plans/${id}"
  status: [200, 204]
```

For richer assertions, use `expect:`:

```yaml
expect:
  status: [200, 201]
  maxResponseTimeMs: 1000
  matchesSchema: true
  # Any non-reserved key under expect asserts a response body field (exact value or Java regex):
  pet.name: "Baby"
  pet.id: "[0-9]+"
  headers:
    Content-Type: "application/json.*"
  bodyContains:
    - "Baby"
  bodyNotContains:
    - "Exception"
  that:
    - "response.home.country == request.home.country"
    - "response.priority >= 10 && response.status != 'ARCHIVED'"
    - "size(response.items) > 0 && response.deletedAt is null"
    - "T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))"
```

### Supported expressions in `expect.that:`
- **Logical operators**: `&&` (or `&`), `||` (or `|`), `!`
- **Comparison operators**: `==` (or `=`), `!=`, `>`, `>=`, `<`, `<=`, `contains`, `matches` (regex), `is null`, `is not null`
- **Operands & functions**:
  - `response.<fieldPath>` (or unqualified `<fieldPath>`) — field from the JSON response body
  - `request.<fieldPath>` (or `$request.<fieldPath>`) — field from the sent JSON request body
  - `$steps.<stepName>.response.<fieldPath>`, `$steps.<stepName>.request.<fieldPath>`, `$steps.<stepName>.status`
  - `$prev.response.<fieldPath>`, `$prev.request.<fieldPath>`, `$prev.status`
  - `status` — HTTP status code integer
  - `responseTimeMs` (or `time`) — response time in milliseconds
  - `size($)` or `size(<fieldPath>)` — number of elements/properties in the root or nested array/object
  - `T(...)` — [SpEL boolean expression](/docs/advanced-topics/dynamic-values) evaluated against the request and response

---

## Cross-Field Correlations (`correlate:` and `--correlations`)

Many APIs enforce conditional cross-field rules that OpenAPI schemas cannot express on their own—for example, when `planType == 'RECURRING'`, `billingSchedule` must be set and `oneTimeSetupFee` must be omitted.

You can define these rules either inside a functional flow file under `correlate:` (or `correlations:`), on an individual step under `correlate:`, or in a standalone YAML file passed via `--correlations` (alias `--correlate`).

### Supported `correlations.yml` Formats (`then:` is optional)

`set:` and `omit:` can be written directly on a rule or nested under `then:`. Three layouts are supported:

1. **Grouped `rules:` under `paths:`** (best when multiple rules share paths):
```yaml
correlations:
  - paths:
      - "/billing-plans"
      - "/billing-plans/{planId}"
      - "/billing-plans*"
    rules:
      - when: "planType == RECURRING"
        set:
          billingSchedule: "MONTHLY"
          gracePeriodDays: 7
        omit:
          - "oneTimeSetupFee"
      - when: "planType == ONE_TIME"
        set:
          oneTimeSetupFee: 99
        omit:
          - "billingSchedule"
          - "gracePeriodDays"
      - when: "tiers[*].mode == METERED"
        set:
          tiers[*].unitField: "SeatCount"
```

2. **Path-keyed map** (matching `refData.yml` & `headers.yml`, including `all:`):
```yaml
all:
  when: "mode == TEST"
  set:
    dryRunFlag: true

/billing-plans:
  - when: "planType == RECURRING"
    set:
      billingSchedule: "MONTHLY"
  - when: "planType == ONE_TIME"
    omit:
      - "billingSchedule"
```

3. **Flat single-rule list** (`- paths: [...]` + `when:` + `set:` / `omit:` or `then:`):
```yaml
correlations:
  - paths:
      - "/orders*"
    when: "items[*].category == 'DIGITAL'"
    set:
      items[*].shippingRequired: false
```

- **`paths`**: Exact paths or wildcard prefixes ending with `*` (such as `"/orders*"` or `"*"`). Rules also apply automatically to every element of a **root JSON array** request body.
- **`when`** (or `if`): Boolean condition evaluated against the outgoing JSON request payload (supports quoted or unquoted literals, field-to-field comparisons like `format.start < format.length`, and per-element array wildcards `[*]`).
- **`set`** (or `then.set`): Field paths and values (supporting `${variables}`, `#(...)` shorthands, and `T(...)` expressions) to set or insert when the condition holds.
- **`omit`** (or `then.omit`): Field paths to remove from the payload when the condition holds.

Pass `--correlations` to apply these rules across **all** live HTTP commands (`cats`, `cats run`, `cats replay`, `cats template`, `cats random`):

```shell
cats -c openapi.yml -s http://localhost:8080 --correlations correlations.yml
```

### How `when:` Rules Interact with Built-In Fuzzers
- **Conditionally required fields**: A guarded `when:` + `set:` rule marks the `set:` target field as **conditionally required** on payload variants where `when:` evaluates to `true`. `OnlyRequiredFields` retains the field on matching variants, and `RemoveFields`, `NullValuesInFields`, and `EmptyStringsInFields` expect `4XX` when removing or nulling it on matching variants (while still expecting `2XX` on variants where `when:` is `false`).
- **Mutation protection**: During fuzzing, CATS automatically protects the field being actively mutated by a fuzzer so correlation rules never overwrite the fuzzer's negative test value.
- **Auto-synthesis**: You can synthesize `correlations.yml` automatically from `4XX` findings using `cats triage -r cats-report -c openapi.yml --export-correlations correlations.yml`.

---

## Migrating from the Legacy `FunctionalFuzzer` Syntax (Deprecated)

:::warning Deprecation Notice
The legacy path-grouped `FunctionalFuzzer` YAML syntax (`cats-global-vars`, top-level `/path:` keys, `httpMethod`, `expectedResponseCode`, `output`, `verify`, `checkBoolean`) is **deprecated** and **will be removed in CATS `16.x.x`**. Please migrate existing functional test files to the sequential `steps:` format.
:::

### Old path-grouped format (deprecated — to be removed in `16.x.x`)

```yaml
cats-global-vars:
  defaultCountry: "RO"

/pet:
  test_1:
    description: Create a Pet
    httpMethod: POST
    name: "My Pet"
    address#country: "${defaultCountry}"
    oneOfSelection:
      pet#type: "Dog"
    expectedResponseCode: 200
    output:
      petId: pet#id
    verify:
      pet#name: "${request#name}"
      pet#id: "[0-9]+"
      checkBoolean: T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))

/pet/{id}:
  test_2:
    description: Get a Pet
    httpMethod: GET
    id: ${petId}
    expectedResponseCode: 200
```

### Equivalent new `steps:` format

```yaml
variables:
  defaultCountry: "RO"

steps:
  - name: "createPet"
    description: "Create a Pet"
    POST: "/pet"
    variant:
      pet.type: "Dog"
    body:
      name: "My Pet"
      address.country: "${defaultCountry}"
    save:
      - petId: "pet.id"
    expect:
      status: 200
      pet.id: "[0-9]+"
      that:
        - "response.pet.name == request.name"
        - "T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))"

  - name: "getPet"
    description: "Get a Pet"
    GET: "/pet/${petId}"
    status: 200
```
