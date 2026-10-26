# Examples of Configuration Files Used by CATS

This folder contains ready-to-use examples of configuration and test files supported by CATS.

---

## 1. Headers File (`--headers files/headers.yml`)

CATS supports both CLI-level headers using `-H` and path/method-scoped headers using `--headers <FILE>`.

[headers.yml](./headers.yml) demonstrates:
- **`all:`** — headers sent on every request
- **Wildcard path prefixes (`"/pets*":`)** — headers sent on all matching sub-paths
- **HTTP method scoping (`"POST /pets":`)** — headers sent only for the specified HTTP method and path
- **Environment variables (`$$API_TOKEN`)** — resolved from the environment / `--envFile` and masked in reports
- **Dynamic shorthand functions (`#(uuid)`)** — freshly generated per request

---

## 2. Web Fuzzing Commons Auth File (`--wfcAuth` / `--authConfig`)

CATS can read a Web Fuzzing Commons authentication file using `--wfcAuth <FILE>` (or `--authConfig <FILE>`). If the file contains multiple authentication entries, select one with `--wfcAuthName <NAME>`.

- [wfc-auth.yml](./wfc-auth.yml) — shows both static authentication headers and a dynamic login flow that extracts a token from the login response.
- [wfc-auth-static-token.yml](./wfc-auth-static-token.yml) — fixed `Authorization` header.
- [wfc-auth-dynamic-token.yml](./wfc-auth-dynamic-token.yml) — JSON login endpoint with token extraction from the response body.
- [wfc-auth-cookie.yml](./wfc-auth-cookie.yml) — form login endpoint that extracts session cookies.
- [wfc-auth-external-token.yml](./wfc-auth-external-token.yml) — token login endpoint hosted outside the API server using `externalEndpointURL`.

---

## 3. Reference Data File (`--refData files/referenceFields.yml`)

Use `--refData <FILE>` to pin fixed field values or omit fields from generated payloads and fuzzing.

[referenceFields.yml](./referenceFields.yml) demonstrates:
- **`all:`**, **wildcard path prefixes (`"/pets*":`)**, **exact paths (`"/pets/{id}":`)**, and **HTTP method scoping (`"POST /pets":`)**
- **Dot-notation (`pet.id`, `owner.country`)** and `#`-notation (`pet#id`) for nested JSON fields
- **Dynamic shorthand functions (`#(uuid)`)** and SpEL (`T(...)`)
- **`omit: [...]`** to remove fields from outgoing payloads and skip fuzzing them

---

## 4. Cross-Field Correlations File (`--correlations files/correlations.yml`)

Use `--correlations <FILE>` when an API enforces conditional cross-field invariants (for example, when a discriminator or enum field requires or forbids sibling fields).

[correlations.yml](./correlations.yml) demonstrates `paths:` (exact or wildcard `*`), `when:` boolean conditions, and `then:` actions (`set:` and `omit:`).

---

## 5. Functional Flow File (`cats run -c openapi.yml -s <url> files/functionalFuzzer.yml`)

The [FunctionalFlowFuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/functional-fuzzer) executes sequential, stateful multi-step API workflows (`steps:`), starting from schema-valid payloads and headers generated from the OpenAPI contract and overriding only the fields needed for each scenario.

[functionalFuzzer.yml](./functionalFuzzer.yml) demonstrates:
- Top-level `variables:` and flow-scoped `correlate:` rules
- Sequential `steps:` with `name:`, `description:`, `<METHOD>: /path`, `variant:`, `body:`, and `omit:`
- `save:` using both shorthand list (`- id`) and named alias (`- homeId: "home.id"`)
- `expect:` with `status:`, direct field assertions (exact and regex), and `that:` expressions using `request.*`, `response.*`, `$steps.<stepName>.*`, and `$prev.*`

---

## 6. Security Fuzzer File (`cats run -c openapi.yml -s <url> files/securityFuzzer.yml`)

The [SecurityFuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/security-fuzzer) injects custom payload dictionaries or inline payloads into targeted fields or field types using the `steps:` + `fuzz:` syntax.

[securityFuzzer.yml](./securityFuzzer.yml) demonstrates:
- `fuzz.fields` (specific field paths) and `fuzz.fieldTypes` (`string`, `email`, `headers`, `body`)
- `fuzz.payloadsFile` (referencing [xss.txt](./xss.txt)) and inline `fuzz.payloads`
- `fuzz.mode: single` (one field at a time) and `fuzz.mode: cross` (all target fields simultaneously)
- `expect:` assertions (`status:` and `bodyNotContains:`)

---

## 7. Fuzz Configuration (`--fuzzersConfig files/fuzzConfig.properties`)

[fuzzConfig.properties](./fuzzConfig.properties) shows how to override default expected response codes per fuzzer (for example, configuring `DummyAcceptHeaders` to expect `403` instead of `406`).

---

## 8. Custom Mutators (`cats random ... --mutators files/mutators`)

The [mutators](./mutators) folder contains sample mutators ([first.yml](./mutators/first.yml), [second.yml](./mutators/second.yml), [third.yml](./mutators/third.yml) loading [nosql.txt](./nosql.txt)) for `cats random`.

---

## 9. Error Leaks Keywords (`--errorLeaksKeywords files/errorLeaks.txt`)

[errorLeaks.txt](./errorLeaks.txt) contains custom error-leak substrings checked against response bodies.

---

## 10. Paths Ordering (`--pathsRunOrder files/pathsOrder.txt`)

[pathsOrder.txt](./pathsOrder.txt) specifies an explicit execution order for OpenAPI paths.
