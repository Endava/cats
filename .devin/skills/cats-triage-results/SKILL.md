---
name: cats-triage-results
description: "Triage CATS test reports into deduplicated root-cause clusters, mine working --refData from 2XX responses, synthesize --correlations rules, generate OpenAPI contract patches, replay failing tests, verify fixes live (--verify), and compare reports against a baseline."
argument-hint: "[-r cats-report] [-s <server-url>]"
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

# CATS — Report Triage, Live Fix Verification, Replay & Baseline Diffing

Use this skill after any `cats run`, `cats random`, or `cats template` execution to turn hundreds of `Test*.json` files into deduplicated root causes, ready-to-use configuration files, and verified code fixes.

---

## 1. Deduplicate Root Causes & Export Remediation Artifacts (`cats triage`)

Run `cats triage` on the report folder (default `cats-report`):
```bash
cats triage -r cats-report -c <spec> -j \
  --export-refdata refData.yml \
  --export-correlations correlations.yml \
  --export-contract-patch contract-patch.yml \
  --export-reproducers reproducers.sh
```
*(Add `--include-warnings` to include warning test cases in clustering alongside errors.)*

### How to Act on Each Root-Cause Cluster Category
1. **`SERVER_CRASH_5XX` (Severity 0 — Highest Priority)**:
   - Unhandled `500+` server error.
   - Read the representative `Test*.json` file listed in `sampleTestIds` (e.g., `cats-report/Test42.json`) or run `curlReproducer`.
   - Locate the exception in the server logs/codebase, fix the bug, and run `cats triage --verify` (Section 2).
2. **`CROSS_FIELD_CONSTRAINT_4XX` (Severity 1)**:
   - `HappyPath` or `OnlyRequiredFields` was rejected with `400`/`422` due to a domain or cross-field rule (e.g., discriminator `planType == 'RECURRING'` requires `billingSchedule`).
   - Review the exported `correlations.yml`, replace any `"<REQUIRED_VALUE>"` placeholders with valid domain values, and pass `--correlations correlations.yml` on the next CATS run.
3. **`HEADER_CORRELATION_COLLISION` (Severity 1)**:
   - A per-request header was overwritten by runtime correlation. Pin it explicitly in `headers.yml` (`--headers headers.yml`).
4. **`AUTH_CONFIGURATION_GAP` (Severity 2)**:
   - Non-auth-bypass fuzzers received `401`/`403`. Configure valid credentials via `-H` or `--authConfig`.
5. **`UNRESOLVED_PATH_PARAM_404` / `SUSPICIOUS_CORRELATION_404` / `RESOURCE_POOL_DEPLETED_404` (Severity 2)**:
   - Path parameters could not be resolved or were deleted mid-run. Pass the auto-mined `--refData refData.yml` (which extracts real IDs from `2XX` responses in the same report) on the next run.
6. **`CONTRACT_MISMATCH` (Severity 3)**:
   - The API returned an undocumented non-`5XX` status code or response schema mismatch. If the API behavior is intentional, merge the exported `contract-patch.yml` into the OpenAPI specification.

---

## 2. Verify Fixes Live in Seconds (`cats triage --verify`)

After fixing a server bug or updating headers, verify all root-cause clusters against the live server without re-running the full fuzzer suite:
```bash
cats triage -r cats-report --verify -s <server_url> -j
```
- Replays **1 representative test case per root-cause cluster** (automatically resolving `$$ENV` placeholders and applying `-s` / `-H` overrides).
- Marks each cluster with `verificationStatus`: **`"FIXED"`** or **`"STILL_FAILING"`** and records `verifiedStatusCode`.
- Returns exit code `0` if all `SERVER_CRASH_5XX` clusters are `"FIXED"`, or `1` if any `5XX` crash still reproduces.

---

## 3. Replay Individual Test Cases (`cats replay`)

To inspect, replay, or export a single test case (including its upstream stateful producer chain):
```bash
# Replay Test 42 and Test 108 with full request/response logging
cats replay Test42 Test108 -r cats-report -s <server_url> -H "Authorization=Bearer $$API_TOKEN" -v

# Replay Test 42 statefully (re-runs upstream 2XX POST producer step(s) from the report first,
# extracts fresh resource IDs from the live responses, and substitutes them into Test 42!)
cats replay Test42 -r cats-report -s <server_url> --stateful

# Export Test 42 + its upstream producer chain as a reusable --functionalFlow YAML regression test
cats replay Test42 -r cats-report --export-flow reproducer-flow.yml
```

---

## 4. Regression Gating Between Runs (`cats compare`)

Compare a current CATS report against a baseline report to detect new regressions vs. resolved findings:
```bash
cats compare \
  --baseline cats-report-baseline \
  --current cats-report \
  --failOn new-errors,new-5xx,regressions \
  -j
```
- Reports `newFindings`, `resolvedFindings`, `unchangedFindings`, and `regressedFindings` (`warn` $\rightarrow$ `error` or `4XX` $\rightarrow$ `5XX`).
- Exits with code `1` when any condition in `--failOn` is triggered.
