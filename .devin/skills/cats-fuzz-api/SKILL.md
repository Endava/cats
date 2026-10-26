---
name: cats-fuzz-api
description: "Smoke-probe and fuzz live REST APIs with CATS to find 5XX crashes, validation gaps, broken authentication, and security vulnerabilities. Covers cats smoke, cats run (profiles, stateful resource pool, filters), cats random, and spec-less cats template fuzzing."
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

# CATS — Live API Smoke Probing, Stateful Fuzzing & Security Testing

Use this skill when the user wants to test or fuzz a running REST API for bugs, `5XX` crashes, boundary/type-coercion defects, or OWASP API security issues.

---

## Step 1: Always Run `cats smoke` First (Pre-Flight Probe)

Before running a multi-minute fuzzing session, probe every operation once with a happy-path request:
```bash
cats smoke -c <spec> -s <server_url> -j
```
*(Add `--includeDelete` only if you want `DELETE` endpoints probed during the smoke check.)*

Inspect `verdict` in the JSON output:
- **`READY`** — At least one operation returned `2XX` and no `5XX` crashes occurred. Proceed to Step 2.
- **`UNREACHABLE_SERVER`** — All requests failed with I/O errors. Verify the server process and port.
- **`AUTH_FAILURE_ALL_ENDPOINTS`** — Every endpoint returned `401`/`403`. Supply credentials via `-H`, `--headers`, or `--authConfig` and re-run `cats smoke`.
- **`NEEDS_SEED_OR_AUTH_CONFIG`** — Endpoints returned `404`/`4XX`. Check `unresolvedParameters` in the smoke JSON output and seed them via `cats init -c <spec>`, `--urlParams`, or `--refData`.
- **`SERVER_CRASHES_DETECTED`** — Even happy-path requests triggered `5XX` responses. Inspect `operations` in the smoke output immediately.

---

## Step 2: Configure Authentication (`-H`, `--headers`, `--authConfig`)

- **Inline header**: `-H "Authorization=Bearer $$API_TOKEN"` (values starting with `$$` are resolved from environment variables and masked in reports).
- **Headers YAML file (`--headers headers.yml`)**:
  ```yaml
  all:
    Authorization: "Bearer $$API_TOKEN"
  "/admin/users":
    X-Admin-Role: "SUPERUSER"
  ```
- **Declarative Auth (`--authConfig auth.yml`)** — supports `bearer`, `basic`, `apiKey`, `cookie`, `oauth2` (`client_credentials` / `password`), and `loginEndpoint` (auto-logs in and extracts a token/cookie before fuzzing).

---

## Step 3: Run Stateful Profile-Based or Targeted Fuzzing (`cats`)

*(Note: Use the root `cats` command for built-in fuzzers and profiles. The `cats run <file.yml>` subcommand is only for executing custom YAML functional or security flow files.)*

### Recommended Stateful Flags
Always enable stateful resource correlation when fuzzing multi-endpoint CRUD APIs:
- `--srs` (`--smartResourceSeeding` / `--reuseSuccessfulResources`) — extracts resource IDs and values from `2XX` responses (`POST`, `PUT`, `PATCH`, `GET`) and correlates them into downstream `{path}` / query / body parameters.
- `--srw` (`--smartResourceWaves`) — executes fuzzers in 4 waves across all paths (**Wave 1: Seed** $\rightarrow$ **Wave 2: Coverage** $\rightarrow$ **Wave 3: Crash** $\rightarrow$ **Wave 4: Full + Second-Phase Consistency**) and defers `HappyPath` `DELETE`s until consumers finish.
- `--preseedCollectionGets` — runs collection `GET` endpoints before Wave 1 to harvest existing resource IDs.

### Built-In Fuzzer Profiles (`--profile`)
| Profile | When to Use | Command |
| :--- | :--- | :--- |
| **`quick`** | Fast smoke + core boundary/required-field checks (~15 fuzzers) | `cats -c <spec> -s <url> --srs --srw --profile quick` |
| **`ci`** | Balanced functional, boundary, type-coercion & security suite (~45 fuzzers) | `cats -c <spec> -s <url> --srs --srw --profile ci` |
| **`security`** | OWASP API Top 10 (SQLi, NoSQLi, XSS, Command/LDAP/XXE/SSTI injection, SSRF, IDOR, Mass Assignment, Auth Bypass, Security Headers) | `cats -c <spec> -s <url> --srs --srw --profile security` |
| **`coverage`** | Fast positive + combinatorial field-removal blitz to maximize `2XX` operation & code coverage | `cats -c <spec> -s <url> --srs --srw --profile coverage` |
| **`crash`** | High-yield boundary, type-coercion, control-char & injection fuzzers targeting `5XX` server faults | `cats -c <spec> -s <url> --srs --srw --profile crash` |

### Scoped / Targeted Fuzzing
```bash
# Fuzz specific paths and HTTP methods
cats -c <spec> -s <url> --srs -p "/orders,/orders/{orderId}" -X POST,PUT

# Run specific fuzzers (discover names with `cats list -f`)
cats -c <spec> -s <url> --srs -f "HappyPath,RemoveFields,MassAssignment,SqlInjectionInStringFields"

# Bound execution time or error count
cats -c <spec> -s <url> --srs --srw --stopAfterTimeInSec 120 --stopAfterErrors 50

# Blackbox mode: only report 5XX server crashes as errors (ignore 2XX/4XX contract mismatches)
cats -c <spec> -s <url> --srs --srw -b -k
```

---

## Step 4: Continuous Random Fuzzing (`cats random`)

Run continuous mutation-based fuzzing with a mandatory stop condition:
```bash
cats random -c <spec> -s <url> -p /orders -X POST --stopAfterTimeInSec 60 --seed 42
```

---

## Step 5: Fuzzing Without an OpenAPI Spec (`cats template`)

When no OpenAPI contract exists, fuzz any HTTP endpoint directly from a request template:
```bash
cats template -s http://localhost:8080/api/login \
  -X POST \
  -H "Content-Type=application/json" \
  -d '{"username":"admin","password":"secret"}' \
  -t "username,password" \
  --stopAfterTimeInSec 30
```

---

## Step 6: Custom Security Payload Fuzzing (`--securityFuzzerFile security.yml`)

Use `SecurityFuzzer` with the `steps:` + `fuzz:` syntax to inject inline `payloads` or custom wordlists (`payloadsFile`) across specific `fields`, `fieldTypes` (`string`, `email`, `headers`, `body`), or simultaneously across multiple fields (`mode: cross`), and chain subsequent steps in `steps:` to verify second-order SQLi / stored XSS:

```yaml
steps:
  - name: "sqliAndXssCheck"
    description: "Verify POST /users rejects SQLi and stored XSS payloads"
    POST: "/users"
    fuzz:
      fields: [username, bio]
      fieldTypes: [string, email]
      payloads:
        - "' OR '1'='1"
        - "<script>alert(1)</script>"
      payloadsFile: "wordlists/xss.txt"
      mode: cross   # 'single' (one field at a time) or 'cross' (all target fields simultaneously)
    expect:
      status: [201, 400, 422]
      bodyNotContains: ["SQLException", "syntax error"]
```

Run with:
```bash
cats run -c <spec> -s <url> security.yml
```
