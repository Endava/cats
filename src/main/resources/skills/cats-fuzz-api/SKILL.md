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
- **`READY`** — At least one operation returned `2XX` and no `5XX` crashes occurred. Proceed to Step 2, but still inspect `notFound404` and `validation4xx`: `READY` does not mean every operation succeeded.
- **`NO_OPERATIONS_EXECUTED`** — Nothing was probed. Check `-p` / `-X` / tag filters and that the contract has operations.
- **`UNREACHABLE_SERVER`** — All requests failed with I/O errors. Verify the server process and port.
- **`AUTH_FAILURE_ALL_ENDPOINTS`** — Every endpoint returned `401`/`403`. Supply credentials (Step 2) and re-run `cats smoke`.
- **`NEEDS_SEED_OR_AUTH_CONFIG`** — Endpoints returned `404`/`4XX`. Check `unresolvedParameters` in the smoke JSON output and seed them via `cats init -c <spec>`, `--urlParams`, or `--refData`.
- **`SERVER_CRASHES_DETECTED`** — Even happy-path requests triggered `5XX` responses. Inspect `operations` in the smoke output immediately.

---

## Step 2: Configure Authentication (`-H`, `--headers`, `--wfcAuth`, `--basicAuth`)

**`$$VAR` environment placeholders resolve only when they are the ENTIRE value.** `"Bearer $$API_TOKEN"` is sent literally. Put the full header value in the env var, and always single-quote `$$` in shells (bash expands `$$` inside double quotes to its process ID):
```bash
export API_AUTH='Bearer eyJ...'
```
- **Inline header**: `-H 'Authorization=$$API_AUTH'` (resolved from the environment or `--envFile` / auto-detected `./.env`, and masked in reports).
- **Headers YAML file (`--headers headers.yml`)**:
  ```yaml
  all:
    Authorization: "$$API_AUTH"
  "/admin/users":
    X-Admin-Role: "SUPERUSER"
  ```
  Pin headers under `all` or a path key: method-scoped keys (`"POST /orders"`) are not protected from runtime header correlation.
- **Login / token flows (`--wfcAuth auth.yml [--wfcAuthName <name>]`)** — Web Fuzzing Commons auth file: `auth: [{name, fixedHeaders: [{name, value}]}]` or `auth: [{name, loginEndpointAuth: {endpoint | externalEndpointURL, verb, payloadRaw | payloadUserPwd, token: {extractFrom, extractSelector, sendIn, sendName}, expectCookies}}]`. Without `--wfcAuthName`, CATS starts with the first entry and switches on `401`/`403`.
- **Basic auth**: `--basicAuth user:pass`.
- **Refreshing tokens**: `--authRefreshScript ./get-token.sh [--authRefreshInterval 300]` replaces header values set to `auth_script`.

---

## Step 3: Run Stateful Profile-Based or Targeted Fuzzing (`cats`)

*(Note: Use the root `cats` command for built-in fuzzers and profiles. The `cats run <file.yml>` subcommand is only for executing custom YAML functional or security flow files.)*

### Stateful Behavior (On by Default)
- **Runtime resource reuse is ON by default** (`--reuseSuccessfulResources`; disable with `--no-reuseSuccessfulResources`): IDs and values from `2XX` responses are correlated into downstream `{path}` / query / body parameters.
- **Breadth-first waves are ON by default**: seed, coverage and crash waves run across all paths before the full suite, and `HappyPath` `DELETE`s are deferred until consumers finish. No flag is needed.
- `--preseedCollectionGets` — runs collection `GET` endpoints first to harvest existing resource IDs (bounded by `--maxPreseedCollectionGets`).
- `-R "field=value"` / `--refData refData.yml` — pins existing resource IDs or domain-validated values (see `/cats-write-tests` Section 5 for key-notation rules).
- `--correlations correlations.yml` — applies cross-field `when` $\rightarrow$ `set` / `omit` rules to every built-in fuzzer request (see `/cats-write-tests` Section 3).

**Choose reporting filters for the intended follow-up:** `--srs` (`--skipReportingForSuccess`) reduces report size while
retaining errors and warnings for ordinary triage. Keep successful `2XX` exchanges for `cats triage --export-refdata` and
successful setup steps in `cats replay --stateful`. `--srw` (`--skipReportingForWarning`) additionally removes warnings;
`-b -k` filters findings too. Comparison coverage is inferred from saved tests, so filtering can make an occurrence
`NOT_COMPARABLE` even when its fuzzer ran successfully.

### Expectation Flags (Match the API's Intended Leniency)
- `--no-strictTypes` — type-coercion fuzzers accept `2XX` or `4XX` instead of requiring `4XX`.
- `--no-allowDifferentCaseEnumValues` — `EnumCaseVariantFields` expects `4XX` (the default expects `2XX`, i.e. case-insensitive enums).
- `--allowInvalidEnumValues` — `InvalidValuesInEnumsFields` expects `2XX`.

### Built-In Fuzzer Profiles (`--profile`)
| Profile | When to Use | Command |
| :--- | :--- | :--- |
| **`quick`** | Fast smoke + core boundary/required-field checks (24 fuzzers) | `cats -c <spec> -s <url> --profile quick` |
| **`ci`** | Balanced functional, boundary, type-coercion & security suite (80 fuzzers) | `cats -c <spec> -s <url> --profile ci` |
| **`security`** | OWASP API Top 10 and decoder robustness (SQLi, NoSQLi, XSS, Command/LDAP/XXE/SSTI injection, SSRF, IDOR, Mass Assignment, Auth Bypass, Security Headers) (59 fuzzers) | `cats -c <spec> -s <url> --profile security` |
| **`coverage`** | Fast positive + field-removal fuzzers to maximize `2XX` operation & code coverage (10 fuzzers) | `cats -c <spec> -s <url> --profile coverage` |
| **`crash`** | High-yield boundary, type-coercion, control-char & injection fuzzers targeting `5XX` server faults (65 fuzzers) | `cats -c <spec> -s <url> --profile crash` |

Other profiles: `compliance`, `type-coercion`, `health-check`, `full` (default). Inspect any profile with `cats explain -t PROFILE <name>`.

### Scoped / Targeted Fuzzing
```bash
# Fuzz specific paths and HTTP methods
cats -c <spec> -s <url> -p "/orders,/orders/{orderId}" -X POST,PUT

# Run specific fuzzers (discover names with `cats list -f`)
cats -c <spec> -s <url> -f "HappyPath,RemoveFields,MassAssignment,SqlInjectionInStringFields"

# Base64 decoder boundaries: 12 values per eligible field, at most 257 characters each
cats -c <spec> -s <url> -f Base64DecoderBoundaryFields

# Combinatorial optional-field removal (ONEBYONE + optimal strength-2 Kleitman-Spencer covering array; add --interactionStrength 3 for 3-way IPOG)
cats -c <spec> -s <url> -f RemoveFields --fieldsFuzzingStrategy PAIRWISE

# Skip fuzzers everywhere, per path, per HTTP method, or per method + path (`;` separates fuzzers within one scope)
cats -c <spec> -s <url> --skipFuzzers "RandomResources,/health*=Jumbo,GET=ControlChars,DELETE /orders/*=ControlChars;Emojis"

# Bound execution time or error count
cats -c <spec> -s <url> --stopAfterTimeInSec 120 --stopAfterErrors 50

# Rate-limited APIs (staging gateways, SaaS sandboxes):
# CATS automatically retries HTTP 429 (and 503 with Retry-After) up to --maxRateLimitRetries (default 3),
# honouring Retry-After (seconds or HTTP-date, capped at --maxRetryAfterInSec, default 30) and AIMD pacing.
# Set --maxRequestsPerMinute to stay under known gateway quotas, or --maxRateLimitRetries 0 to disable retries:
cats -c <spec> -s <url> --maxRequestsPerMinute 300 --maxRateLimitRetries 3 --maxRetryAfterInSec 30

# Blackbox mode: retain 5XX (except 501) and high-severity security findings; -k drops suppressed results/successes
cats -c <spec> -s <url> -b -k
```

`Base64DecoderBoundaryFields` targets non-enum strings declared with `format: byte` or `contentEncoding: base64` / `base64url`,
plus recognized cursor/pagination fields. It tests valid and malformed padding/tails near 64- and 256-character boundaries,
accepts normal `2XX`/`4XX` handling, and uses existing reporting for failures. Raw `format: binary` uploads and ordinary
token fields are excluded unless they also match a Base64 declaration or a recognized cursor name. It is included in
`ci`, `security`, `crash` and the default full run. The cursor fuzzer's four existing structure probes remain separate;
running both produces 16 distinct values per cursor field. These checks have no warm-up loop and do not establish a specific CVE.

---

Blackbox security findings require explicit `CONFIRMED` confidence, CATS's highest level; automated confirmation remains heuristic. `FIRM`, `TENTATIVE` and unassessed findings are suppressed even if normally errors or promoted by `--strict`. Keyword matches alone cannot promote a security finding. Explicit `-i` remains literal (`-b -i 2xx` suppresses 2XX security findings); exceptional-response, response-time and server-error leak handling remain. This changes reporting and CI gates. Run without `-b` for all findings; `-i 2xx,4xx,501` without `-b` reproduces the former code filter. Use an unfiltered run before `cats triage --export-refdata`. Blackbox uses CATS security types independently of WFC codes. Findings without a matching standard WFC category use tool-specific exports: SSTI `913 CATS_SSTI`, XXE `914 CATS_XXE`, and path traversal `915 CATS_PATH_TRAVERSAL`; their confidence requirement is unchanged.

## Step 4: Continuous Random Fuzzing (`cats random`)

Run continuous mutation-based fuzzing with a mandatory stop condition:
```bash
cats random -c <spec> -s <url> -p /orders -X POST --stopAfterTimeInSec 60 --seed 42
```

---

## Step 5: Fuzzing Without an OpenAPI Spec (`cats template`)

When no OpenAPI contract exists, fuzz any HTTP endpoint directly from a request template. The full endpoint URL is a required **positional** argument (include query params for non-body requests):
```bash
cats template \
  -X POST \
  -H "Content-Type=application/json" \
  -d '{"username":"admin","password":"secret"}' \
  -t "username,password" \
  --stopAfterTimeInSec 30 \
  http://localhost:8080/api/login
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
