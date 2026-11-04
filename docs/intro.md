---
sidebar_position: 1
description: Autonomous REST API fuzzing, OpenAPI contract linting, and stateful functional testing
---

# Introduction

**CATS** (**C**ontract **A**PI **T**esting and **S**ecurity) is an autonomous REST API fuzzer, negative testing engine, and declarative functional testing tool. Give CATS an OpenAPI specification and a target URL, and it automatically generates, executes, and triages thousands of self-healing boundary, type-coercion, OWASP security, and multi-step workflow tests—without writing a single line of test code.

:::caution Target Environment
Run CATS only against local, staging, or dedicated test environments. Because CATS actively mutates payloads, exercises resource lifecycles (`POST`, `PUT`, `PATCH`, `DELETE`), and probes boundary and injection conditions, running it against production systems can modify state or degrade performance.
:::

---

## Quickstart

Make sure CATS is installed ([Installation Guide](/docs/getting-started/installation)):

```bash
brew tap endava/tap && brew install cats
```

### 1. Analyze Readiness & Smoke-Probe the API

Inspect the contract's `0–100` Fuzzability Readiness Score and producer-to-consumer resource chains offline, then send a single `HappyPath` request per operation (skipping `DELETE` by default) to confirm connectivity and authentication:

```bash
cats analyze -c openapi.yml
cats smoke -c openapi.yml -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN"
```

*(Values prefixed with `$$` are resolved from environment variables or `.env` and automatically masked in logs and HTML/JSON reports.)*

### 2. Fuzz the API

Choose the depth that matches your workflow:

```bash
# Blackbox crash hunting: report only 5XX server errors and omit ignored results
cats -c openapi.yml -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN" -b -k

# Curated CI or OWASP Security profile with stateful resource correlation (enabled by default)
cats -c openapi.yml -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN" --profile ci
cats -c openapi.yml -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN" --profile security

# Full contract-aware suite (all active Field, Header, and HTTP fuzzers)
cats -c openapi.yml -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN"
```

Open `cats-report/index.html` to inspect the interactive HTML report, or pass `--tui` to follow execution live in the terminal.

### 3. Run Stateful Functional Flows

Write multi-step CRUD workflows, cross-field rules, polling checks, and data-driven scenarios in minimal `steps:` YAML—CATS generates the rest of every request from your contract:

```bash
cats run -c openapi.yml -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN" flow.yml
```

### 4. Triage Failures & Verify Fixes

Cluster failing tests by root cause, auto-generate remediation files (`refData.yml`, `correlations.yml`, `reproducers.sh`), and verify fixes live against the server:

```bash
cats triage -r cats-report -c openapi.yml \
  --export-refdata refData.yml \
  --export-correlations correlations.yml \
  --export-reproducers reproducers.sh

# Replay 1 representative request per root-cause cluster to confirm FIXED vs STILL_FAILING
cats triage -r cats-report --verify -s https://api.example.com -H "Authorization=Bearer $$API_TOKEN"
```

---

## What CATS Covers

| Capability | Scope | Where to Learn More |
|:--|:--|:--|
| **Field Fuzzers (`117`)** | Numeric/string boundaries, `exclusiveMinimum`/`Maximum`, `multipleOf`, `minItems`/`maxItems`, `minProperties`, `const`/`not`, merged `oneOf` branches, type coercion, SQL/NoSQL/LDAP/Command/SSTI/XXE/XSS/SSRF/Path Traversal injections, `readOnly` & undeclared Mass Assignment, HTTP Parameter Pollution, and Unicode edge cases. | [Field Fuzzers](/docs/fuzzers/field-fuzzers) |
| **Header Fuzzers (`39`)** | Content negotiation (`Accept`, `Content-Type`), `Content-Length`/`Transfer-Encoding`, CRLF injection, security headers, duplicate/extra headers, and Unicode/control-character header mutations. | [Header Fuzzers](/docs/fuzzers/header-fuzzers) |
| **HTTP Fuzzers (`35`)** | `HappyPath`, authentication bypass, BOLA/IDOR, HTTP verb tampering & method-override tunneling (`X-HTTP-Method-Override`), `Idempotency-Key` conflict detection, `PUT` idempotency, `ETag` preconditions, cross-parent isolation, and post-delete availability. | [HTTP Fuzzers](/docs/fuzzers/http-fuzzers) |
| **Stateful Functional Flows** | Declarative `steps:` YAML workflows with schema-backed payload generation, cross-step `$steps` / `save` correlation, `expect.that` assertions, `poll` / `delayMs` async helpers, `repeat`, `forEach` data tables, and `correlate` rules. | [Functional Flow Fuzzer](/docs/fuzzers/special-fuzzers/functional-fuzzer) |
| **Continuous & Spec-less Fuzzing** | Time- or count-bounded mutation fuzzing with `36` built-in mutators (`cats random`), custom dictionary fuzzing (`SecurityFuzzer`), and template-based fuzzing without an OpenAPI spec (`cats template`). | [Mutators](/docs/getting-started/mutators) · [Template Fuzzer](/docs/fuzzers/special-fuzzers/template-fuzzer) |
| **Contract Linters (`58`)** | Offline OpenAPI 3.0/3.1 structural, semantic, and REST naming checks: ambiguous `oneOf` variants, default/example schema conformance, contradictory bounds, request/response symmetry, pagination & error consistency, and REST path role analysis. | [Contract Linters](/docs/fuzzers/linters) |

---

## Choosing a Command

| Goal | Command |
|:--|:--|
| Score contract readiness & view producer-consumer resource chains | `cats analyze -c openapi.yml --mermaid` |
| Scaffold starter `headers.yml`, `refData.yml`, and `functionalTests.yml` | `cats init -c openapi.yml` |
| Install CATS skills for Devin, Claude Code, Codex, OpenCode, or Cursor | `cats init --skills` |
| Verify server reachability and auth (1 request per endpoint) | `cats smoke -c openapi.yml -s http://localhost:8080` |
| Run fast PR/CI fuzzing suite | `cats -c openapi.yml -s http://localhost:8080 --profile ci` |
| Run OWASP API Security Top 10 audit | `cats -c openapi.yml -s http://localhost:8080 --profile security` |
| Execute stateful YAML functional or security flows | `cats run -c openapi.yml -s http://localhost:8080 flow.yml` |
| Cluster report failures & verify fixes live | `cats triage -r cats-report --verify -s http://localhost:8080` |
| Replay a test with its upstream `2XX` producer chain | `cats replay Test42 --stateful` |
| Export a failing test + producer chain as a runnable `steps:` flow | `cats replay Test42 --export-flow reproducer.yml` |
| Gate CI against a previous baseline report | `cats compare -b ./baseline-report -t ./cats-report` |
| Lint OpenAPI contract offline | `cats lint -c openapi.yml --strict` |
| Explain any fuzzer, linter, profile, mutator, or error reason | `cats explain --type fuzzer ReadOnlyFieldsMassAssignment` |
