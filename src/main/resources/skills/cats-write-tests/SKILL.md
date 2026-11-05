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

# Generate a schema-valid request payload for a specific path and method (-p is required).
# Use -L 2 (the fuzzing default; generate defaults to 4) and the same --seed as your fuzzing run
# to see the generated payload that correlation `when:` rules are evaluated against.
cats generate -c <spec> -p /orders -X POST -L 2 --seed 42 --pretty

# oneOf/anyOf bodies produce several payloads; cap them with -l
cats generate -c <spec> -p /orders -X POST -l 5 --pretty
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
5. **Only override fields in `body:` and `query:` that matter to the scenario** — with `base: merge` (default), CATS populates all other required schema fields automatically and omits unrequested optional query parameters on `GET`/`DELETE`. Use `base: onlyRequired` to start a body from required fields only, or `base: empty` to start from `{}`.
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
    # Optional oneOf/anyOf variant selection: a schema-name suffix (`variant: MeteredPlan`) or a
    # field match (any listed field equal, case-sensitive). Falls back silently to the first variant.
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
    # Optional initial delay (ms) and polling for async/eventual-consistency transitions
    delayMs: 100
    poll:
      maxAttempts: 5
      intervalMs: 200
    query:
      includeDetails: true
    expect:
      status: 200
      that:
        - "$steps.createPlan.status == 201"
        - "response.id == $steps.createPlan.response.id"
        - "response.name == $prev.response.name"

  - name: "createPlansByCurrency"
    description: "Data-driven step executed once per row in forEach"
    POST: "/billing-plans"
    forEach:
      - { currency: "EUR", minPrice: 10 }
      - { currency: "USD", minPrice: 12 }
    body:
      currency: "${currency}"
      unitPrice: "${minPrice}"
    expect:
      status: [200, 201]
      currency: "${currency}"

  - name: "exhaustRateLimitOrOneOffQuota"
    description: "Execute N-1 setup calls and assert expect on the Nth call"
    POST: "/billing-plans/${id}/activate"
    repeat: 2
    expect:
      status: [400, 409, 422, 429]

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

*(Alternative step syntax: `method: POST` + `path: "/billing-plans"`, or `ALL: "/path"` for every documented method. `path:` may also be a map of path-parameter values. `expect.headers:` asserts response headers.)*

### Running a Functional Flow
```bash
cats run -c <spec> -s <server_url> flows.yml
```
- `cats run` **ignores `--correlations`** for flow steps. Put cross-field rules in the flow's top-level `correlate:` block or a step-level `correlate:` block. Step `body:` / `omit:` / `path:` / `query:` values always win over `correlate:` rules.
- `cats run` does **not** read `--refData` as input. It treats the file as a `${var}` template and writes `<name>_replaced.yml` with values captured by `save:`. `-R` is not available.
- Supported auth/config flags: `-H`, `--headers`, `--queryParams`, `--wfcAuth`, `--basicAuth`, `--oneOfSelection`, `--seed`, `-o`.

---

## 3. Authoring Cross-Field Correlation Rules (`--correlations correlations.yml`)

When an API enforces conditional field dependencies (e.g., when a discriminator or enum field has a specific value, certain sibling fields become required or forbidden), define them in `correlations.yml` so all built-in CATS fuzzers satisfy the invariant.

**Scope:** `--correlations` applies to the root `cats` command and `cats template`. `cats run` flows ignore it; use the same rule syntax inside the flow's `correlate:` block.

### Supported `correlations.yml` Formats (`then:` is optional)

1. **Grouped `rules:` under `paths:` (Best when multiple rules share paths)**:
```yaml
correlations:
  - paths:
      - "/billing-plans"
      - "/billing-plans/{planId}"
      - "/billing-plans/batch*"
    rules:
      - when: "planType == 'RECURRING'"
        set:
          billingSchedule: "MONTHLY"
          gracePeriodDays: 7
        omit:
          - "oneTimeSetupFee"
      - when: "planType == 'ONE_TIME'"
        set:
          oneTimeSetupFee: 99
        omit:
          - "billingSchedule"
          - "gracePeriodDays"
      - when: "tiers[*].mode == 'METERED'"
        set:
          tiers[*].unitField: "SeatCount"
```
*(Mutually exclusive branches like `RECURRING` / `ONE_TIME` keep payloads consistent at runtime, but only the branch matching the generated payload marks its `set:` fields required. See Critical Rule 1.)*

2. **Path-Keyed Map (`all:`, exact contract paths, or `prefix*`; unlike `refData.yml` / `headers.yml` there is NO HTTP method scoping, so a `"POST /billing-plans":` key silently never matches)**:
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

3. **Flat Single-Rule List (`- paths: [...]` + `when:` + `set:` / `omit:` or `then:`)**:
   - Ideal when there is only one rule per path group.
   - Rules also apply automatically to every element of a **root JSON array** request body (e.g. batch endpoints).

### How `when:` Rules Interact with Built-In Fuzzers (Critical Rules)
1. **`when:` is evaluated twice, on two different payloads**:
   - **(a) Required-field analysis**, once per operation, on the **raw generated payload**: before `--refData`, runtime resource reuse, or any other correlation rule is applied. If `when:` is `true` there, every `set:` key becomes required: `OnlyRequiredFields` keeps it, and `RemoveFields` / `NullValuesInFields` / `EmptyStringsInFields` expect **`4XX`** for it.
   - **(b) Runtime**, on every request, on the already-mutated payload (after `--refData` and resource reuse), to apply `set:` / `omit:`.
   - Consequence: if the trigger value is not what the generator picked (inspect it with `cats generate -c <spec> -p <path> -X POST -L 2 --seed <n>`), the `set:` is still applied at runtime, but nothing is marked required. Pinning the trigger with `--refData` does **not** change this.
   - **Pattern to force a branch and mark its dependents required:** guard on the trigger's presence and set the trigger in the same rule:
     ```yaml
     - paths: ["/billing-plans"]
       when: "planType is not null"
       set:
         planType: "RECURRING"
         billingSchedule: "MONTHLY"
     ```
   - **Never add `& <targetField> is null` to `when:`**: the target is already populated in the generated payload, so the rule is `false` during analysis and the target stays optional.
2. **Which `when:` clauses mark fields required**:
   - `when: "X is not null"` with `set: { X: ... }` never marks `X` required (self-presence guard), so it is safe for value generators such as unique names.
   - Any other `when:` that is `true` marks **every** `set:` key required on **every HTTP method** of the matched paths, including `GET` query params (CATS builds a synthetic JSON payload from query/path params). `when: "ruleType is not null & name is not null"` therefore marks `name` required on `POST`. Only use it if `name` really is required.
   - A rule without `when:` never marks anything required, but it **inserts** the `set:` fields into every matching request, including `GET` query strings. Scope `paths:` tightly.
3. **Fields under active mutation are protected from correlations** (use `--refData` for boundary-fuzzer targets):
   - When a fuzzer mutates a field (`mutationTargets`), correlations do not overwrite or re-insert it. So correlations cannot stop `MinLengthExactValuesInStringFields`, `MaxLengthExactValuesInStringFields`, or `AllFieldsAtMaxBoundary` from sending a 1-char or max-length string into that field.
   - Pin closed-domain / unique / checksum fields in **`refData.yml`** instead (see Section 5 for the key-notation rules that make boundary fuzzers skip them).
   - Correlations run **last**: on fields that are not being mutated, `set:` overwrites `--refData` values and runtime-correlated IDs.
4. **Evaluation semantics**:
   - `==` / `!=` / `<` / `>` compare numerically when both sides are numbers, otherwise **case-insensitively**. `contains` and `matches` are **case-sensitive**.
   - **`!=` is `true` when the field is absent** (e.g. on `GET` query payloads or after `RemoveFields`). Prefer `==` triggers.
   - **Always quote string literals** (`'RECURRING'`). An unquoted right-hand token that matches a field name is read as that field. An unquoted token containing `.` or `#` (e.g. `a@b.com`, `1.2.3`) never matches.
   - `|` and `&` split expressions even as single characters outside quotes, so quote regexes: `code matches '^(A|B)$'`.
5. **Typed `set:` values**: `#(...)` and `${...}` always produce **JSON strings** in `correlations.yml` (and `refData.yml`). For numeric/boolean fields write YAML literals (`unitPrice: 15`, `active: true`). Lists and objects are inserted as-is.
6. **Silent no-ops**:
   - A nested `set:` (`billing.schedule`) whose parent object is missing from the payload (e.g. removed by `OnlyRequiredFields`) does nothing. Only top-level fields and existing parents are created.
   - Only the first `[*]` array in a rule is expanded. If that array is missing or empty, the whole rule is skipped, including its non-wildcard keys.
7. **Case-variant enums & AST/Operator correlations**:
   - Because `==` is case-insensitive, `when: "expression.operator == 'GT'"` still fires when `EnumCaseVariantFields` sends `gt` or `GTe`. `EnumCaseVariantFields` expects `2XX` by default; pass `--no-allowDifferentCaseEnumValues` if the API is case-sensitive.
   - For recursive AST/expression schemas (`Expression` with `type`, `field`, `operator`, `value`, `children`), cover every operator/type invariant in `correlations.yml`:
     - `when: "expression.type == 'CONDITION'"` $\rightarrow$ `set:` valid `field`, `operator`, `value` and `omit: ["expression.children"]`.
     - Numeric operators (`GT`, `GTE`, `LT`, `LTE`) $\rightarrow$ `set:` a numeric `field` (e.g. `"amount"`) and numeric `value` (e.g. `100`).
     - Reference operators (`IN_WATCHLIST`, `NOT_IN_WATCHLIST`) $\rightarrow$ `set:` a string ID `value`.
     - Scalar operators (`EQ`, `NEQ`) $\rightarrow$ `set:` a scalar `value`.
     - Composite `when: "expression.type == 'NOT'"` $\rightarrow$ `set:` `expression.children` to a 1-element list containing a valid `CONDITION` object, and `omit: ["expression.field", "expression.operator", "expression.value"]`.
     - Composite `when: "expression.type == 'AND' || expression.type == 'OR'"` $\rightarrow$ `set:` `expression.children` to a 2-element list of valid `CONDITION` objects, and `omit: ["expression.field", "expression.operator", "expression.value"]`.
### Supported `when:` and `expect.that:` Operators
- **Logical connectives**: `&&` or `&` (`AND`), `||` or `|` (`OR`; `&&` binds tighter than `||`). No parentheses grouping.
- **Comparison operators**: `==`, `=`, `!=`, `>`, `>=`, `<`, `<=`, `contains`, `matches` (regex), `is null` (`== null`), `is not null` (`!= null`, `notNull`)
- **Right-hand side values**: quoted strings (`'RECURRING'`, recommended), unquoted simple constants (`RECURRING`, only when no field has that name), numbers (`15`), booleans (`true`), dynamic expressions (`'${myVar}'`), or another JSON field path (`format.start < format.length`)
- `when:` also accepts the alias `if:`; `paths:` accepts `path:`; both `correlations:` and `correlate:` are accepted as the root key.
- **Wildcard array correlation (`[*]`)**: `tiers[*].mode == 'METERED'` paired with `tiers[*].unitField` in `set:` or `omit:` expands and evaluates per array element
- **Prefixes & functions in `expect.that:` (and `${...}` expressions)**:
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

Anywhere in `flows.yml`, `correlations.yml`, `refData.yml`, or `headers.yml`, you can use the placeholders below.

Types: in flow `body:` / `query:` / `path:` values, a value that is entirely `#(...)` is converted to a number/boolean when it looks like one. In `correlations.yml`, `refData.yml` and flow `correlate:` blocks, results are always **JSON strings**, so use YAML literals for numeric/boolean fields.

- **`${varName}`** — value extracted by a prior step (`save:`) or defined in `variables:`
- **`$$ENV_VAR`** — environment variable read from the process environment (or `--envFile` / `./.env`) and masked in reports. It resolves **only when it is the entire value** (`"$$API_AUTH"`, not `"Bearer $$API_TOKEN"`). Single-quote it in shells, because bash expands `$$` inside double quotes.
- **`#(uuid)`** — deterministic/seeded RFC 4122 UUID v4
- **`#(ulid)`** — 26-char lexicographically sortable ULID
- **`#(email)`** — random valid `@cats.io` email address
- **`#(alphanumeric(N))`** / **`#(alpha(N))`** / **`#(numeric(N))`** / **`#(hex(N))`** — random string of length `N` (or `#(alpha(min,max))` range; also work without args for default 12-char length)
- **`#(upper(expr))`** / **`#(lower(expr))`** / **`#(capitalize(expr))`** / **`#(trim(expr))`** / **`#(reverse(expr))`** / **`#(length(expr))`** — composable string helpers (e.g. `#(upper(alphabetic))`, `#(upper(alpha(6)))`, `#(lower(alphanumeric(8)))`)
- **`#(randomInt(min,max))`** / **`#(bool)`** — random integer in `[min, max)` or random boolean (`true` / `false`)
- **`#(pick(VAL_1,VAL_2,VAL_3))`** / **`#(oneOf(VAL_1,VAL_2))`** — picks one value from the list (supports nested functions like `#(pick(upper(alpha(4)),numeric(4)))`)
- **`#(today)`** / **`#(todayPlus(days))`** / **`#(todayMinus(days))`** — ISO-8601 `YYYY-MM-DD` date
- **`#(now)`** / **`#(nowPlusDays(days))`** / **`#(nowMinusDays(days))`** / **`#(nowPlusHours(hours))`** — ISO-8601 UTC timestamp
- **`#(epoch)`** / **`#(epochMillis)`** — current Unix epoch timestamp in seconds or milliseconds
- **`#(sha256(expr))`** / **`#(md5(expr))`** / **`#(base64(expr))`** / **`#(base64Url(expr))`** / **`#(urlEncode(expr))`** — composable encoding & hashing helpers

---

## 5. Reference Data & Headers (`--refData refData.yml`, `-R`, `--headers headers.yml`, `-H`)

### When to Use `--refData` (`-R`) vs. `--correlations` vs. `--headers` (`-H`)
- **`--headers` (`headers.yml`) / `-H`**: For HTTP headers (`-H` for global headers; `headers.yml` when scoping headers by path or HTTP method like `"POST /orders"`). Only `all` and path keys (not method-scoped keys) protect a header from runtime header correlation.
- **`--refData` (`refData.yml`) / `-R`**: For **pinned reference or domain-validated values** (such as pre-existing resource IDs or fields with business checksums like Luhn card numbers `payload#number: "4111111111111111"`). Pinned fields are not overwritten by runtime resource reuse, and are skipped by `MinLengthExactValues` / `MaxLengthExactValues` / `AllFieldsAtMaxBoundary` / numeric boundary fuzzers, **as long as the key-notation rules below are followed**.
- **`--correlations` (`correlations.yml`)**: For **conditional cross-field rules** (`when` $\rightarrow$ `set` / `omit`) that keep interdependent fields consistent while still allowing all boundary and field fuzzers to test those fields.

Both `refData.yml` and `headers.yml` support:
- **`all`** — applied to all paths
- **Exact contract path templates (`"/customers/{customerId}/orders"`) and wildcard prefixes (`"/orders*"`)**
- **HTTP method scoping (`"POST /orders"`, `"PUT /orders*"`)** — applied only to the specified HTTP method. Precedence: exact method+path > exact path > method+wildcard > wildcard > `all`.
- **Nested fields**: `#`-notation (`shippingAddress#country`) or dot-notation (`shippingAddress.country`)
- **Dynamic expressions (`#(...)`, `T(...)`, `$$ENV_VAR`)** and **`omit: [...]`** to remove fields from payloads and skip fuzzing them

**refData key rules (pinning vs. value replacement):**
- Every notation and scope **replaces the value** in requests, but protection is narrower:
  - **Boundary fuzzers** only skip a field pinned under a path-only, wildcard, or `all` key **in `#` notation**. With dot notation (`shippingAddress.country`) they still run against `shippingAddress#country`; the pinned value is sent instead and the test is wasted.
  - **Runtime resource reuse** only respects path-only, wildcard, or `all` keys (either notation).
  - Method-scoped keys (`"POST /orders"`) replace values but protect from neither.
- Pinning a path parameter disables runtime resource reuse for it, so every non-`DELETE` test hits the same ID.
- `#(...)` values are sent as JSON strings; use YAML literals for numeric/boolean fields.
- `cats run` (functional flows) does not apply `--refData` to requests.

```yaml
all:
  tenantId: "tenant_default"
  omit:
    - "internalTrace"

"/customers*":
  region: "EU"

# Pins: path-only key + `#` notation so boundary fuzzers and resource reuse skip them
"/customers/{customerId}/orders":
  customerId: "cust_1001"
  shippingAddress#country: "DE"
  quantity: 2

# Method-scoped key: value replacement only
"POST /customers/{customerId}/orders":
  requestId: "#(uuid)"
  omit:
    - "legacyField"
    - "shippingAddress#zip"
```
