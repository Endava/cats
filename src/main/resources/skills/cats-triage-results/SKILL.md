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

**Prerequisite:** the report must contain the `2XX` and warning tests. If the run used `--srs` / `--srw` (`--skipReportingForSuccess` / `--skipReportingForWarning`) or `-k`, `--export-refdata`, `--include-warnings` and `cats replay --stateful` have almost nothing to work with. Re-run without them.

### Review Exported Artifacts Before Reusing Them
- **`correlations.yml`**: rules are only synthesized from `400`/`409`/`422` rejections of requests CATS expected to succeed (`HappyPath`, `OnlyRequiredFields`, or `2XX`-only expectations), and only when the message demands presence (`REQUIRE` → `set:`), absence (`REMOVE` → `omit:`) or uniqueness (`UNIQUE` → self-guarded `when: "<field> is not null"` + `set:`). Rules are grouped per path under `rules:`; each carries a `# from:` comment with the source error.
  - Placeholders are typed: from the contract schema when `-c` is passed (first enum value, bounded number, `#(email)`/`#(uuid)`/`#(today)`, length-bounded `#(alphanumeric(N))`, one-element arrays), otherwise from message hints (`numeric`, `array`) or the value type in the failing request. Still replace them with real domain values (existing IDs, valid codes).
  - `when:` uses a trigger whose value is mentioned in the error message, or a discriminator-named field (`*type`, `*mode`, `*status`…) holding an enum-like value (declared as an enum in the contract, when supplied). Rules without a trustworthy trigger are emitted **unconditionally** (no `when:`); unconditional rules never mark fields as required. Confirm every trigger exists in the generated payload (see `/cats-write-tests` Section 3).
  - Pass `-c <spec>` so field names guessed from free text are validated against the contract; without it, multi-word guesses (`tax number` → `taxNumber`) are emitted as-is.
- **`refData.yml`**: values come only from `2XX` exchanges that were expected to succeed. Values sent by path-mutating fuzzers and identifiers later removed by a successful `DELETE` are excluded. Created IDs from `POST` responses are filed under the real item path template (`/orders` + `id` → `"/orders/{orderId}": orderId: …`) when the contract or report contains it. Pinned path params disable runtime resource reuse for that param.
- **`contract-patch.yml`**: only adds undocumented status codes. It never fixes a test whose fuzzer expected a different code family.
- **`reproducers.sh`**: masked values are expanded from the environment (`"${Authorization}"`), and the script aborts with a hint when a required variable is unset.

### Root-Cause Cluster Categories
1. **`SERVER_CRASH_5XX` (Severity 0 — Highest Priority)**: unhandled `500+` error. Read the representative `Test*.json` listed in `sampleTestIds` (e.g., `cats-report/Test42.json`) or run `curlReproducer`, fix the backend, then verify (Section 2).
2. **`FIELD_DOMAIN_CONSTRAINT_4XX` (Severity 1)**: the server rejected a value the fuzzer was mutating (e.g. `MinLengthExactValuesInStringFields` sent a duplicate `name`). Only `--refData` can fix it: pin the field under a path-only key in `#` notation, or tighten the contract constraints. No correlation rule is synthesized for it.
3. **`CROSS_FIELD_CONSTRAINT_4XX` (Severity 1)**: `HappyPath` / `OnlyRequiredFields` rejected with `400`/`422`, or any test expecting `2XX` that got `400`/`422` on a path that received a synthesized correlation rule. The remediation quotes the matching synthesized rule when one exists.
4. **`HEADER_CORRELATION_COLLISION` (Severity 1)**: a `4XX` response to a request whose header was overwritten by runtime correlation. Pin it in `headers.yml` under `all` or the path key (method-scoped keys are not protected) or via `-H`. A `5XX` is always reported as `SERVER_CRASH_5XX`.
5. **`AUTH_CONFIGURATION_GAP` (Severity 2)**: non-auth-bypass fuzzers received `401`/`403`. Fix credentials (`-H`, `--headers`, `--wfcAuth`, `--basicAuth`; see `/cats-fuzz-api` Step 2).
6. **`VALIDATION_BYPASS_2XX` (Severity 2)**: the fuzzer expected `4XX` but the server accepted the input. The remediation names the relevant flag (`--no-strictTypes`, `--allowInvalidEnumValues`, `--no-allowDifferentCaseEnumValues`) or contract/correlation fix.
7. **404 categories (Severity 2)**:
   - `UNRESOLVED_PATH_PARAM_404`: path has `{params}`; the remediation names the producer (`POST` on the path up to the last `{param}`) and the `--urlParams` to pass.
   - `UNRESOLVED_BODY_REFERENCE_404`: path has no `{params}` and the request body references a missing entity; the remediation lists the body ID fields to pin.
   - `ROUTE_NOT_FOUND_404`: path has no `{params}` and no body; check the `-s` base URL or query references.
   - `SUSPICIOUS_CORRELATION_404`: a path param was correlated from a non-ID field (`name`, `title`, `label`…).
   - `RESOURCE_POOL_DEPLETED_404`: an earlier successful `DELETE` on the same or a parent path removed the resource.
8. **`CONTRACT_MISMATCH` (Severity 3)**: catch-all, including undocumented status codes and other unexpected codes. Read the `remediation` text: it says whether the code is undocumented (contract patch) or the request was schema-valid but rejected (refData/correlations/contract constraints).

Clusters are sorted by severity, occurrences, category and operation, so results do not depend on test numbering.

### Per-Cluster Decision Tree
For each cluster, open one `sampleTestIds` file and read `fuzzer`, `scenario` (note `is required [TRUE|FALSE]`), `expectedResult`, and the response body. Then:
1. **`5XX`** → backend bug. Do not hide it with `--refData` / `--correlations`, unless `HappyPath` itself crashes because the seed data is invalid.
2. **`401`/`403`** → auth configuration. Re-run `cats smoke` after fixing.
3. **`404`**:
   - Path contains `{p}` → pin `p` in `refData.yml` under the **exact contract path template**, or pass `--urlParams p:value`.
   - Path has no `{}` → the body references a missing entity → pin that body ID field in `refData.yml` (use `#` for nested fields: `customer#id`).
   - `RESOURCE_POOL_DEPLETED_404` → an earlier `DELETE` consumed the resource; pin an existing ID or ignore.
4. **`400`/`422` while `2XX` was expected**:
   1. The error names a field whose valid value **depends on another field's value** → `--correlations` rule (see `/cats-write-tests` Section 3, "generated payload" rule).
   2. `FIELD_DOMAIN_CONSTRAINT_4XX`, or the error names **the fuzzed field itself** (`MinLengthExactValuesInStringFields`, `MaxLengthExactValuesInStringFields`, `AllFieldsAtMaxBoundary`, `ExamplesFields`; uniqueness, checksum, closed vocabulary, existing ID) → pin it in `refData.yml` under a **path-only or `all` key** using **`#` notation** for nesting. Correlations cannot help because fields under active mutation are protected from them. If the contract's `minLength`/`maxLength`/`maximum` is simply wrong, fix the contract instead.
   3. `RemoveFields` / `NullValuesInFields` / `EmptyStringsInFields` with `is required [FALSE]` but the server requires the field → always required: add it to `required:` in the contract; only required conditionally: add a correlation rule.
   4. `EnumCaseVariantFields` → the API is case-sensitive: pass `--no-allowDifferentCaseEnumValues` (default expects `2XX`), or fix the server.
5. **`2XX` while `4XX` was expected (`VALIDATION_BYPASS_2XX`)**:
   1. `scenario` says `is required [TRUE]` but the contract marks the field/param optional → a correlation `when:` matched where it shouldn't (often GET query params). Tighten `paths:` or the `when:` guard.
   2. Type-coercion fuzzers and lenient parsing is intended → `--no-strictTypes`.
   3. `InvalidValuesInEnumsFields` and unknown values are intentionally accepted → `--allowInvalidEnumValues`.
   4. Required in the contract but the server applies a default → fix the contract (`required: false` + `default:`).
   5. Otherwise → missing server-side validation (real bug).
6. **`CONTRACT_MISMATCH`**: merge `contract-patch.yml` only when the `remediation` says the code is **undocumented** and the behavior is intended. A remediation of "align service status code or fuzzer expectations" is never fixed by the patch; apply steps 4/5.
7. **`NOT_MATCHING_RESPONSE_SCHEMA`** (reported as warnings; needs `--include-warnings` and a run without `--srw`) on `oneOf`/`anyOf` responses → try re-running with `--resolveXxxOfCombinationForResponses`. If the warning persists, fix the discriminator mapping or the response schema in the contract.
8. **HTTP-semantics and behavior oracles** (cluster as `CONTRACT_MISMATCH` keyed by the reason; most are warnings, so use `--include-warnings`). These are server behavior bugs: never fix them with `contract-patch.yml`, `--refData` or `--correlations`.
   - `Repeated PUT returned 201 Created` (`PutIdempotency`, error) → the PUT handler reports "created" for an existing resource. Return `200`/`204` on update (RFC 9110 §9.3.4).
   - `PUT on existing resource returned 201 Created` (`MisleadingCreatePut`, error) → a `GET` immediately before the `PUT` returned `2XX` proving the resource exists, yet the `PUT` returned `201 Created` instead of `200`/`204` (or the `GET` handler wrongly returns `200` for missing resources).
   - `PUT did not perform a full replacement` (`PartialUpdatePut`, warning) → `PUT` must replace the entire representation (RFC 9110 §9.3.4), not merge fields like `PATCH`. Either an omitted optional field retained its previous value or a shared writable field differed on the subsequent `GET`.
   - `Rejected update partially applied` (`RejectedUpdateSideEffects`; error for PATCH, warning for PUT) → the request was rejected with `4XX`, yet the canary value `cats-atomic-patch`/`cats-atomic-put` it carried was persisted. Validate the whole body before saving, or wrap the update in one transaction.
   - `Filter parameter ignored` (`FilterParameterHonored`) → items not matching `?field=value` were returned. Check that the filter is applied in the query. Treat it as a data-exposure issue when the parameter scopes ownership (`ownerId`, `tenantId`).
   - `OPTIONS Allow header not matching contract` (`OptionsAllowConformance`) → the message lists undocumented and missing methods. Undocumented verbs may be unintended endpoints, so disable them or document them. Missing verbs usually mean a framework-generated `OPTIONS` that doesn't know the routes. `HEAD`/`OPTIONS` are ignored.

---

## 2. Verify Fixes Live in Seconds (`cats triage --verify`)

After fixing a server bug or updating headers, verify all root-cause clusters against the live server without re-running the full fuzzer suite:
```bash
export Authorization='Bearer eyJ...'
cats triage -r cats-report --verify -s <server_url> -j
```
- Reports mask sensitive headers and query params as **`$$<HeaderNameWithOnlyAlphanumerics>`** (e.g. `Authorization` → `$$Authorization`, `X-API-Key` → `$$XAPIKey`), whatever env var you originally used. Export a variable with exactly that name, or override the header with `-H 'Authorization=Bearer eyJ...'`.
- Replays **1 representative request per root-cause cluster** as-is (no upstream producers are re-run, so IDs may be stale).
- Marks each cluster with `verificationStatus` and records `verifiedStatusCode` / `verificationNote`:
  - **`FIXED`**: `SERVER_CRASH_5XX` now returns `< 500`; other clusters return a code allowed by the test's `expectedResult` (e.g. `[400, 422]`, `4XX`).
  - **`STILL_FAILING`**: the original failure still reproduces.
  - **`INCONCLUSIVE`**: the replay cannot prove anything: a masked variable is missing, the request could not be sent, an unrelated `401`/`403` came back, a `404` came back for a test that originally got something else (stale IDs; use `cats replay TestN --stateful`), or the cluster is contract-only drift.
- Returns exit code `0` if all `SERVER_CRASH_5XX` clusters are `FIXED`, or `1` if any crash cluster is `STILL_FAILING` or `INCONCLUSIVE`.

---

## 3. Replay Individual Test Cases (`cats replay`)

To inspect, replay, or export a single test case (including its upstream stateful producer chain):
Replay resolves masked `$$<HeaderName>` placeholders from the environment / `--envFile` and fails fast if one is missing; `-H` overrides the recorded header.
```bash
# Replay Test 42 and Test 108 with full request/response logging
cats replay Test42 Test108 -r cats-report -s <server_url> -H 'Authorization=Bearer eyJ...' -v

# Print equivalent curl commands without executing
cats replay Test42 -r cats-report --curl

# Re-run every error (and warning) test from cats-summary-report.json
cats replay --errors --warnings -r cats-report -s <server_url>

# Replay Test 42 statefully (re-runs upstream 2XX POST producer step(s) from the report first,
# extracts fresh resource IDs from the live responses, and substitutes them into Test 42).
# Requires the successful POST tests to be in the report (no --srs).
cats replay Test42 -r cats-report -s <server_url> --stateful

# Export Test 42 + its upstream producer chain as a reusable --functionalFlow YAML regression test
cats replay Test42 -r cats-report --export-flow reproducer-flow.yml
```

---

## 4. Regression Gating Between Runs (`cats compare`)

Compare a current CATS report against a baseline report to detect new regressions vs. resolved findings:
```bash
cats compare \
  -b cats-report-baseline \
  -t cats-report \
  --failOnNewWarnings \
  -j
```
- `-b/--baseline` and `-t/--target` accept a `cats-report` folder or a `cats-summary-report.json` file. Comparison is scoped to shared `(path, httpMethod, fuzzer)` triples.
- JSON fields: `newErrors`, `newWarnings`, `resolvedErrors`, `resolvedWarnings` (each a list of `{httpMethod, path, fuzzer, level, reason, httpResponseCode, count}`), `unchangedErrors`, `unchangedWarnings`, `sharedScopes`, `onlyInBaselineScopes`, `onlyInTargetScopes`, `executionTimeDeltaSec`.
- Exits with code `1` on new errors (`--failOnNewErrors`, default `true`), or also on new warnings with `--failOnNewWarnings`.
- To gate during the fuzzing run itself instead, pass `--baseline <report>` (plus optional `--failOnNewWarnings`) to the root `cats` command.
