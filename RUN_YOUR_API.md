# Running CATS Against Your API for the First Time

CATS is designed to be **point-and-run**: give it your OpenAPI specification and your server URL, and it automatically orders endpoints (`POST` before `GET`/`PUT`/`DELETE`), reuses IDs from `2XX` responses, and generates self-healing tests.

Here is the practical 5-minute workflow for your first run, from local sanity check to CI/CD pipeline.

---

## Step 1: Inspect Your Contract & Sanity-Check Connectivity (`analyze`, `init`, `smoke`)

Before running thousands of mutations, you can inspect your contract's dependency graph in `< 1s`, scaffold starter config files if needed, and run a 1-request-per-endpoint live smoke check:

```shell
# 1. Inspect the contract's 0-100 Fuzzability Score and resource dependency tree (offline, only needs -c)
cats analyze -c openapi.yml

# 2. (Optional) Generate starter headers.yml, refData.yml (for orphan path params), and functionalTests.yml
cats init -c openapi.yml

# 3. Probe one happy-path request per endpoint to verify --server and authentication
export API_TOKEN="your-jwt-or-api-key"
cats smoke -c openapi.yml -s https://staging.example.com \
  -H "Authorization=Bearer $$API_TOKEN"
```

> **Tip**: Prefixing an environment variable with `$$` (like `$$API_TOKEN`) keeps the secret masked in HTML/JSON reports and console logs while sending the real value over the wire. CATS also auto-loads `./.env` if present.

**What to look at**:
- If most endpoints return `2XX` or `4XX`, your URL and auth are working.
- If everything fails with `401`/`403` or connection errors, CATS will print a hint at the end — fix `--server` or `-H` before moving to Step 2.

---

## Step 2: Run Your First Fuzzing Pass

Depending on what you want to fix first, pick **one** of these three modes:

### Option A: Hunt for `5XX` Server Crashes First (Recommended First Run)
When testing an API for the first time, OpenAPI contracts often have minor documentation drift (undocumented `400` codes or slightly different error bodies). Use **blackbox mode (`-b -k`)** to ignore everything except **`5XX` server errors**:

```shell
cats -c openapi.yml -s https://staging.example.com \
  -H "Authorization=Bearer $$API_TOKEN" \
  -b -k
```
- **What to aim for**: **`0` errors**. Every error reported with `-b -k` is a real unhandled exception or `500 Internal Server Error` in your service.

### Option B: Fast Validation & Security Smoke Test (`--profile quick`)
Runs 24 core fuzzers (missing required fields, nulls, boundary numbers, invalid enums, SQLi, auth bypass, malformed JSON) in seconds:

```shell
cats -c openapi.yml -s https://staging.example.com \
  -H "Authorization=Bearer $$API_TOKEN" \
  --profile quick
```

### Option C: Interactive Terminal UI (`--tui`)
Watch live wave/path progress, drill down from any fuzzer/reason/path with `Enter`, filter by Errors (`e`) / Warnings (`w`), search tests (`/`), step through test details with `n`/`p`, copy the `curl` command (`c`), or press `?` for the shortcuts guide:

```shell
cats -c openapi.yml -s https://staging.example.com \
  -H "Authorization=Bearer $$API_TOKEN" \
  --tui
```

---

## Step 3: Inspect & Reproduce Findings

At the end of a run, CATS writes an interactive HTML report to `cats-report/index.html` and prints a summary:

- **Errors (`E`)**:
  - `5XX` responses (unhandled server crashes),
  - Requests that should have been rejected (`4XX`) when invalid data was sent, but the server accepted them (`2XX`),
  - Broken authentication or exposed `writeOnly` fields.
- **Warnings (`W`)**:
  - The server returned an expected status code (e.g. `400`), but the response body didn't match the error schema in your OpenAPI spec, or returned an undocumented status code.

### Reproduce Any Test in 1 Second
Every test is saved as `cats-report/Test<N>.json`. You can re-execute it or print a ready-to-paste `curl` command for a bug report:

```shell
# Re-run Test 42 against the server
cats replay Test42

# Print the exact curl command for Test 42
cats replay Test42 --curl

# Not sure what a fuzzer or profile does? Ask CATS:
cats explain --type fuzzer SqlInjectionInStringFields
cats explain --type profile security
```

---

## Step 4: Supplying External IDs (Only If Needed)

CATS automatically chains resources created during the run (`POST /orders` -> `GET /orders/{orderId}`) and can also pre-seed IDs from collection `GET` endpoints (`--preseedCollectionGets`).

If your API has path parameters that **cannot** be created via `POST` (for example a fixed tenant or organization ID), pass them once via `--urlParams`:

```shell
cats -c openapi.yml -s https://staging.example.com \
  --urlParams "tenantId:acme-corp,region:eu-west-1"
```

To see the exact dependency order CATS will use for your contract:
```shell
cats list --paths --executionOrder -c openapi.yml
```

---

## Step 5: Adding CATS to Your CI/CD Pipeline

A good CI pipeline starts strict on `5XX` crashes and progressively tightens contract compliance:

### Stage 1 — Gate Every PR on Zero `5XX` Crashes or Zero Regressions vs. a Baseline
```shell
# Option A: Fail on any 5XX crash
cats -c openapi.yml -s https://ci-env.example.com \
  -H "Authorization=Bearer $$API_TOKEN" \
  --profile ci \
  -b -k \
  --stopAfterTimeInSec 120 \
  --reportFormat JUNIT \
  --qualityGate "errors<1"

# Option B: Compare apples-to-apples against a baseline report and fail ONLY on new regressions
cats -c openapi.yml -s https://ci-env.example.com \
  -H "Authorization=Bearer $$API_TOKEN" \
  --profile ci \
  --baseline ./baseline-cats-report

# Or diff two existing report folders offline:
cats compare -b ./baseline-cats-report -t ./cats-report
```

### Stage 2 — Enforce Contract & Input Validation Compliance
Once your `5XX` errors are fixed and your OpenAPI spec matches your API's `4XX` responses, remove `-b -k`:
```shell
cats -c openapi.yml -s https://ci-env.example.com \
  -H "Authorization: Bearer $$API_TOKEN" \
  --profile ci \
  --stopAfterTimeInSec 180 \
  --qualityGate "errors<1,warn<10"
```

### Stage 3 — Nightly Deep Fuzzing & Security Sweep
Run the full 160+ fuzzer suite (plus continuous random mutations for any remaining time budget):
```shell
cats -c openapi.yml -s https://staging.example.com \
  -H "Authorization=Bearer $$API_TOKEN" \
  -b -k \
  --stopAfterTimeInSec 600 --fillTimeBudget \
  --qualityGate "errors<1"
```

---

## Quick Reference: Which `--profile` Should I Use?

| Profile | When to Use | Fuzzers Run |
|---|---|---|
| `health-check` | First 10-second check that `--server` and auth headers work | `HappyPath` (`1` fuzzer) |
| `quick` | Fast local smoke test during development | `24` core fuzzers |
| `ci` | Pull-request CI/CD gate (balanced speed vs. coverage) | `51` fuzzers |
| `crash` | Maximum `5XX` fault detection on boundary, injection & control chars | `40` high-yield crash fuzzers |
| `security` | OWASP injection, auth bypass, IDOR, mass assignment & state checks | `41` security fuzzers |
| `compliance` | OWASP API Security Top 10 (2023) audit | `43` compliance fuzzers |
| `type-coercion` | Test framework/JSON deserialization coercion (`"123"` as `123`, etc.) | `16` coercion fuzzers |
| `coverage` | Fast positive + combinatorial field-removal pass to maximize code coverage | `9` coverage fuzzers |
| `full` *(default)* | Comprehensive nightly or pre-release run | All `160+` fuzzers |
