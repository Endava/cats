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

:::info `cats run` Scope & Flags
- `cats run` applies external `--headers` (`-H`), `--correlations`, and `--refData` (`-R`) files as lower-precedence defaults before flow-level and step-level `headers:`, `correlate:`, `body:`, `omit:`, `path:`, and `query:` overrides. More specific values always win (`OpenAPI defaults < --headers/-H < flow headers: < step headers: < step omit:`).
- Use `--stepTags` / `--skipStepTags` (or flow-level `stepTags:` / `skipStepTags:`) to run a tagged subset of steps without colliding with OpenAPI operation `--tags`.
- To export a `--refData` file populated with variables captured by `save:`, pass `--export-refdata=FILE` (alias `--exportRefData=FILE`).
- Supported auth/config flags on `cats run`: `-H`, `--headers`, `--queryParams`, `-Q`, `--refData`, `-R`, `--correlations`, `--export-refdata`, `--stepTags`, `--skipStepTags`, `--wfcAuth`, `--basicAuth`, `--oneOfSelection`, `--seed`, `-o`, `--failOn`, `--qualityGate`, `--baseline`, `--failOnNewWarnings`.
:::

---

## Sequential `steps:` Syntax

A functional flow file can define optional `imports:`, flow-level `stepTags:` / `skipStepTags:`, typed `variables:`, flow-scoped `headers:`, flow-scoped `correlate:` rules, and an ordered list of HTTP `steps:`:

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
- **`tags`** (or `tag`): Optional list of step tags (e.g. `tags: [smoke, psp]`) for subset execution via flow-level `stepTags:` / `skipStepTags:` or CLI `--stepTags` / `--skipStepTags`.
- **`body`**: Map, list, scalar, or standalone `"${varName}"` / `"${varName?}"` variable reference overriding the request body. Nested maps merge into existing JSON objects, and dot-notation (`home.country`) or `#`-notation (`home#country`) can target nested fields directly. All YAML types (`integer`, `number`, `boolean`, lists, nested maps) and explicit `null` values are preserved. Set a field to `"cats_remove_field"` or use an optional placeholder `"${varName?}"` (when `varName` is `null` or unset) to omit the field from the payload.
- **`path`**, **`query`**, **`headers`**: Maps of path parameters, query parameters, and HTTP headers for the step (`headers:` also accepts a standalone `"${varName}"` / `"${varName?}"` reference to a map variable). OpenAPI-documented headers are generated automatically and only need to be listed in `headers:` when overriding a specific value. Set a header value to `null` (or `"${var?}"` when unset/`null`) to remove it.
- **`omit`**: List of field paths (and `headers.<name>` entries) to remove from the generated request payload or headers. Supports variable expansion (`omit: "${fieldsToOmit}"` for a list variable, or `omit: ["${field}"]`).
- **`variant`**: Selects a specific `oneOf`/`anyOf` schema branch either by schema name suffix (`variant: "DogRequest"`) or by matching discriminator/property values (`variant: { pet.type: "Dog" }`). Explicit variant criteria take precedence over body fields when choosing the branch.
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

## Reusing Flows (`imports:`), Step Tags, Typed Variables, and Validation

### Composing shared flow files with `imports:`

A flow file can import one or more other flow files using `imports:` (paths are resolved relative to the importing file):

```yaml
# services/adyen-mock/flows.yml
imports:
  - "../../common/psp-adapter/flows.yml"

stepTags: [adapter]

variables:
  route: "XeleratorMockECOM"
  expiryMonthExpired: 12
  refundRequiresAmount: false
  webhookHeaders:
    X-Signature: "mock-sig"
```

- **Merging order**: Imported files load first in listed order, followed by the importing file. Local `variables:` and `headers:` override imported keys; local `correlate:` rules and `steps:` are appended after imported ones; local `stepTags:` / `skipStepTags:` replace imported ones when specified.
- **Imports-only files**: A child file can omit `steps:` completely and only declare `imports:`, `stepTags:`, and `variables:` overrides.
- **Diamond imports and cycles**: Diamond imports (two files importing the same shared file) are deduplicated so shared steps execute only once. Missing import files and import cycles fail fast at load time.

### Step `tags:`, flow-level `stepTags:`, and `--stepTags` / `--skipStepTags`

Tag individual steps with `tags: [psp, smoke]` (or `tag: adapter`) and select which steps run either inside the flow file (`stepTags:` / `skipStepTags:`) or on the command line (`--stepTags` / `--skipStepTags`):

```shell
cats run -c openapi.yml -s http://localhost:8080 flows.yml --stepTags=smoke
cats run -c openapi.yml -s http://localhost:8080 flows.yml --skipStepTags=destructive,slow
```

- **Precedence**: CLI `--stepTags` / `--skipStepTags` override flow-level `stepTags:` / `skipStepTags:` when supplied. Within a run, skip tags take precedence over include tags (case-insensitive matching).
- **Separate from OpenAPI `--tags`**: `--stepTags` filters flow `steps:` and does not filter OpenAPI operations.
- **Untagged steps**: When `stepTags` is active, untagged steps are excluded and CATS logs a warning listing their names.

### Typed `variables:` and Optional Placeholders (`${var?}`)

Values in top-level `variables:` preserve their native YAML types (`integer`, `number`, `boolean`, `null`, lists, and nested maps) when referenced standalone as `"${varName}"`, including whole-object `body: "${webhookBody}"` and whole-map `headers: "${webhookHeaders}"`. Global variables are resolved in dependency order, so a derived variable in an imported file can reference a variable defined in the importing child file.

- **Sending `null` vs. omitting a field**:
  - `"${var}"` with `var: null` sends JSON `null` (preserving `null` vs. absent semantics for negative tests and `PATCH` updates).
  - `"${var?}"` **omits** the body field, nested field, list element, query parameter, or header whenever `var` is `null` or unset (and resolves to `""` when embedded inside a larger string like `"prefix-${suffix?}"`).
  - You can also drop a field explicitly with `"cats_remove_field"` or variable-expanded `omit: "${fieldsToOmit}"`.
- **Coercing `$$ENV_VAR` values**: Because environment variables are strings, use explicit shorthand casts such as `#(int($$EXPIRY_MONTH))`, `#(float($$RATE))`, `#(bool($$DRY_RUN))`, or `#(str(${code}))` (see [Dynamic Values](../../advanced-topics/dynamic-values.md)).

### Load-Time Fail-Fast Validation

Before sending any HTTP request, CATS validates the resolved and tag-filtered flow and fails immediately on:
- Missing imported files, malformed imported YAML, or import cycles.
- Cyclic or undefined `${varName}` references in global `variables:`, steps, `omit:`, or `headers: "${var}"`.
- Non-map values supplied to `headers: "${var}"`.
- A selected step referencing `${varName}`, `$steps.<name>.*`, or `$prev.*` produced only by a step that was excluded by `stepTags` / `skipStepTags`, or used before a later step saves it.

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

2. **Path-keyed map** (`all:`, exact contract paths, or `prefix*` — note that unlike `refData.yml` and `headers.yml`, `correlations.yml` path keys do not use HTTP method prefixes):
```yaml
all:
  when: "mode == 'TEST'"
  set:
    dryRunFlag: true

/billing-plans:
  - when: "planType == 'RECURRING'"
    set:
      billingSchedule: "MONTHLY"
  - when: "planType == 'ONE_TIME'"
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

- **`paths`** (or `path`): Exact paths, wildcard prefixes ending with `*` (such as `"/orders*"` or `"*"`), or HTTP method-scoped paths (`"POST /orders"`, `"PUT /orders*"`). Rules also apply automatically to every element of a **root JSON array** request body.
- **`when`** (or `if`): Boolean condition evaluated against the outgoing JSON request payload (supports quoted string literals `'RECURRING'`, numbers, booleans, field-to-field comparisons like `format.start < format.length`, and per-element array wildcards `[*]`). Always quote string literals (`'RECURRING'`) so tokens containing `.` or `#` or matching a field name are not misinterpreted. Note that `!=` evaluates to `false` when the field is absent (use `is null` to test absence).
- **`set`** (or `then.set`): Field paths and values (supporting `${variables}`, `#(...)` shorthands, and `T(...)` expressions) to set or insert when the condition holds. Standalone `#(...)` and `T(...)` calls preserve numeric and boolean JSON types automatically.
- **`omit`** (or `then.omit`): Field paths to remove from the payload when the condition holds.

Pass `--correlations` to apply these rules to the root `cats` command, `cats run`, and `cats template`:

```shell
cats -c openapi.yml -s http://localhost:8080 --correlations correlations.yml
```

### How `when:` Rules Interact with Built-In Fuzzers
- **Two-stage evaluation (`when:`)**:
  1. **Required-field analysis** runs once per operation on the generated payload **after static `--refData` overrides are applied** (inspectable via `cats generate -c openapi.yml -p /billing-plans -X POST --seed 42 --pretty`). If `when:` evaluates to `true` on that baseline payload, every `set:` target field is marked **conditionally required** (`OnlyRequiredFields` keeps it, and `RemoveFields`, `NullValuesInFields`, and `EmptyStringsInFields` expect `4XX` when removing or nulling it).
  2. **Runtime application** runs on every outgoing request payload (after `--refData` and runtime resource reuse) to apply `set:` and `omit:`.
- **Forcing a branch & self-presence guards**:
  - To force a specific discriminator/enum branch and mark its dependent fields as required, either pin the trigger in `--refData` or guard on the trigger's presence (`when: "planType is not null"`) and set both the trigger and its dependent field in `set:`.
  - A self-presence guard (`when: "name is not null"` with `set: { name: "Plan-#(alphanumeric(6))" }`) never marks `name` itself as required, making it safe for unique value generators.
- **Mutation protection**: During fuzzing, CATS automatically protects the field being actively mutated by a fuzzer (`mutationTargets`) so correlation rules never overwrite the fuzzer's negative test value. To stop boundary fuzzers (`MinLengthExactValues`, `MaxLengthExactValues`, `AllFieldsAtMaxBoundary`) from mutating a closed-domain or checksum field, pin that field in `--refData` instead.
- **Auto-synthesis**: You can synthesize `correlations.yml` automatically from `400`/`409`/`422` findings using `cats triage -r cats-report -c openapi.yml --export-correlations correlations.yml`.

---

## Multipart Operations

Steps can target operations whose request body is `multipart/form-data`. Each body property becomes a part, and properties declared as `type: string` and `format: binary` are sent as files. Use `cats_file:<path>` to send the exact bytes of a file (see [Reference Data](../../getting-started/reference-data-file.md#sending-files-in-multipart-requests)). To omit a part in a step, list its property name under `omit:`; to send extra parts not declared in the schema, include them under `body:`.

Top-level `variables:` are resolved once per flow, so a generated value can be shared by several steps. The example below sends the same upload twice with the same `Idempotency-Key` and expects the second call to be recognized as a replay:

```yaml
variables:
  idempotencyKey: "T(java.util.UUID).randomUUID().toString()"

steps:
  - name: "firstUpload"
    POST: "/documents/doc-1/attachments"
    headers:
      Idempotency-Key: "${idempotencyKey}"
    body:
      file: "cats_file:samples/contract.pdf"
      category: "CONTRACT"
    status: 200
    expect:
      outcome: "APPLIED"

  - name: "replayUpload"
    POST: "/documents/doc-1/attachments"
    headers:
      Idempotency-Key: "${idempotencyKey}"
    body:
      file: "cats_file:samples/contract.pdf"
      category: "CONTRACT"
    status: 200
    expect:
      outcome: "REPEATED"
```

A `#(...)` or `T(...)` value written directly in a flow-level or step-level header (without a variable) is evaluated for each step, so every step gets a fresh value. Flow-level and step-level `headers:` (and step `omit:` / `null` header removals) always take precedence over external `--headers` (`headers.yml`) and `-H` defaults, so headers explicitly configured in the flow are never overwritten by `--headers`.

---

## Migrating from the Legacy `FunctionalFuzzer` Syntax (Removed in 16.0.0)

:::warning Removed in CATS 16.0.0
The legacy path-grouped `FunctionalFuzzer` YAML syntax (`cats-global-vars`, top-level `/path:` keys, `httpMethod`, `expectedResponseCode`, `output`, `verify`, `checkBoolean`) was removed in CATS `16.0.0`. Migrate existing functional test files to the sequential `steps:` format below.
:::

### Old path-grouped format (removed in `16.0.0`)

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
