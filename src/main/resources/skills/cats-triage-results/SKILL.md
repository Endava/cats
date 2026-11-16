---
name: cats-triage-results
description: "Triage the problems of CATS test reports per operation (root-cause category, diagnosis, remediation), mine working --refData from 2XX responses, synthesize --correlations rules, generate OpenAPI contract patches, replay failing tests, verify fixes live (--verify), and compare reports against a baseline."
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

Use this skill after any `cats run`, `cats random`, or `cats template` execution to turn hundreds of `Test*.json` files into a short list of problems, ready-to-use configuration files, and verified code fixes.

**Vocabulary (the same in the HTML report, `cats-summary-report.json` and `cats triage`):**
- A **problem** is a set of failing tests with the same problem key: what the check judged, never the response body by default. Its `problemId` (8 hex characters) is the same in the HTML card footer, the summary JSON (`problems[].id`, `testCases[].problemId`) and triage. Problems span operations.
- An **occurrence** is one problem on one operation (`METHOD path`). Triage classifies and remediates occurrences.
- **Kinds** (`problems[].kind`), first match wins:
  - `SERVER_ERROR`: any `5XX`, keyed by the server message whatever the result reason (the reasons are listed).
  - `ERROR_MESSAGE`: a `4XX` rejection, keyed by the verdict and the server message (`Unexpected response code: N` and `Unexpected behaviour N` are one verdict).
  - `RESPONSE_VIOLATION`: the response breaks a rule. Schema mismatches are keyed by the declared response schema (`$ref` name, or `METHOD path status` when inline) with the validator messages as `variants`; content type, missing headers, error leaks, sensitive data and writeOnly exposure by the violation.
  - `STATUS_CODE`: the status code is the finding (`Undocumented response code`, `Not implemented`, Allow-header checks), keyed by reason and status, with the server messages as `variants`.
  - `ACCEPTED_REQUEST`: a `2XX`/`3XX` where the fuzzer expected a rejection, keyed by verdict, status family and fuzzer. The body is evidence (`evidence.details`, `evidence.confidence` of the most confident test), never identity.
  - `NO_RESPONSE`: I/O errors, `9XX` pseudo statuses and contract linter results.
- `errorTemplate`/`errorTemplateId` is the **server message**, and only exists for `SERVER_ERROR` and `ERROR_MESSAGE` problems (and as `STATUS_CODE` variants). A `2XX`/`3XX` test never has one. The text is stored once per problem; tests keep only `errorTemplateId`.
- Readers must check `problemKeyVersion` (currently `1`); triage rejects other versions with exit code `2`.

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
*(Warnings are triaged by default, like the problems of the HTML report. Add `--errors-only` to triage error tests only.)*

The JSON is `problems[]` (in report rank order: errors first, then crashes, specific accepted-input findings, rejections, response violations, status codes, generic accepted input) → `occurrences[]`, each with `category`, `severityRank`, `diagnosis`, `remediation`, `fields`, `fuzzers`, `sampleTestIds`, `curlReproducer`, `attribution` and, with `--verify`, `verificationStatus` / `verifiedStatusCode` / `verificationNote`. An occurrence takes the category of most of its tests; its representative (reproducer, `--verify`) is the lowest-numbered test of that category. `totalProblems` and `totalOccurrences` give the size of the work list.

**Prerequisite:** the report must contain the `2XX` and warning tests. If the run used `--srs` / `--srw` (`--skipReportingForSuccess` / `--skipReportingForWarning`) or `-k`, `--export-refdata`, warning problems and `cats replay --stateful` have almost nothing to work with. Re-run without them.

### Review Exported Artifacts Before Reusing Them
- **`correlations.yml`**: rules are only synthesized from `400`/`409`/`422` rejections of requests CATS expected to succeed (`HappyPath`, `OnlyRequiredFields`, or `2XX`-only expectations), and only when the message demands presence (`REQUIRE` → `set:`), absence (`REMOVE` → `omit:`) or uniqueness (`UNIQUE` → self-guarded `when: "<field> is not null"` + `set:`). Rules are grouped per path under `rules:`; each carries a `# from:` comment with the source error.
  - Placeholders are typed: from the contract schema when `-c` is passed (first enum value, bounded number, `#(email)`/`#(uuid)`/`#(today)`, length-bounded `#(alphanumeric(N))`, one-element arrays), otherwise from message hints (`numeric`, `array`) or the value type in the failing request. Still replace them with real domain values (existing IDs, valid codes).
  - `when:` uses a trigger whose value is mentioned in the error message, or a discriminator-named field (`*type`, `*mode`, `*status`…) holding an enum-like value (declared as an enum in the contract, when supplied). Rules without a trustworthy trigger are emitted **unconditionally** (no `when:`); unconditional rules never mark fields as required. Confirm every trigger exists in the generated payload (see `/cats-write-tests` Section 3).
  - Pass `-c <spec>` so field names guessed from free text are validated against the contract; without it, multi-word guesses (`tax number` → `taxNumber`) are emitted as-is.
- **`refData.yml`**: values come only from `2XX` exchanges that were expected to succeed. Values sent by path-mutating fuzzers and identifiers later removed by a successful `DELETE` are excluded. Created IDs from `POST` responses are filed under the real item path template (`/orders` + `id` → `"/orders/{orderId}": orderId: …`) when the contract or report contains it. Pinned path params disable runtime resource reuse for that param.
- **`contract-patch.yml`**: only adds undocumented status codes. It never fixes a test whose fuzzer expected a different code family.
- **`reproducers.sh`**: masked values are expanded from the environment (`"${Authorization}"`), and the script aborts with a hint when a required variable is unset.

### Occurrence Categories
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

Problems keep the rank of the report and occurrences are sorted by tests, so results do not depend on test numbering.

Problems identified by the server message carry `errorTemplate` and `errorTemplateId`, mined with Drain (He et al., ICWS 2017) from the full 4XX/5XX response at report time, with variable parts masked:
- `<INPUT>`: a value the request sent (body, query, form or path parameter), even when the server echoes only a fragment of it.
- `<FIELD>`: a field name the request sent.
- `NUM`, `UUID`, `TIMESTAMP`, `PATH`, `URL`, `'STR'` / `"STR"`, `[LIST]`: generic variable parts. Quoted code identifiers are kept, because they name the crash site: `(reading 'id')`, `KeyError: 'name'`, `Cannot invoke "String.length()"`.
- `<*>`: a token that differs between otherwise identical messages.
- `… @ function@file:line`: the first application stack frame, recognized for the JVM, Node.js, .NET, Python, Go, Ruby and PHP. Two templates with different frames are different crash sites.
- `a; b; c`: validation errors that list several messages (NestJS, FastAPI, ASP.NET, Django REST, Rails, Laravel, Spring) are masked one by one, de-duplicated, sorted and capped at 5, so the same rule gives the same template whatever the order or number of invalid fields.

- `CODE: …`: the error type (the template text starts with it). A code read from a code or type field is kept verbatim when it is a single token of letters and digits (`USR-001`, `err-01`, `E_42`, `ERR:12`), so two codes are two templates; UUIDs, hashes, timestamps and runs of 6 or more digits (`ORD-83749213`) are still masked.

Custom JSON error envelopes are read by field role, from the end of the field name in camelCase, snake_case or kebab-case: `*code`, `*type`, `*kind` give the error type; `*message`, `*msg`, `*description`, `*detail(s)`, `*reason`, `*text` give the messages, and all of them are used when they differ. A single nested `error`, `fault` or `failure` object is unwrapped. When the OpenAPI contract declares the error body of the operation and status (exact code, `4XX`/`5XX` or `default`) and the body matches it, the declared property names give the roles and declared `uuid`/`date-time` properties and names such as `requestId`, `traceId` or `timestamp` are ignored.

HTML error pages (Tomcat, Whitelabel, Werkzeug, Express, Django) are reduced to their title and visible text.

Two crashes with different server messages are different problems (e.g. `Cannot invoke "String.length()"…` vs `Cannot invoke "BigInteger.bitLength()"…`); the same crash under several result reasons is one problem. The HTML report calls the template the "Server message" and never shows template ids; use `problemId` to refer to a problem.

When a fuzzed test fails with the same HTTP status code and online error signature as an unmodified baseline request (`HappyPath` or `OnlyRequiredFields`) on the same operation in the same run, CATS records `sameErrorAsBaselineTest` (e.g. `"Test 3"`) and `sameErrorAsBaselineFuzzer` (e.g. `"HappyPath"`) in `Test*.json` and `cats-summary-report.json`, and `cats triage` marks the occurrence with `attribution: "SAME_AS_BASELINE"` (without needing `--verify`) and tells you to fix the `HappyPath` / seed request for that operation first.

### Per-Occurrence Decision Tree
For each occurrence, open one `sampleTestIds` file and read `fuzzer`, `scenario` (note `is required [TRUE|FALSE]`), `expectedResult`, and the response body. Then:
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
7. **`NOT_MATCHING_RESPONSE_SCHEMA`** (reported as warnings; needs a run without `--srw`; one `RESPONSE_VIOLATION` problem per declared schema) on `oneOf`/`anyOf` responses → try re-running with `--resolveXxxOfCombinationForResponses`. If the warning persists, fix the discriminator mapping or the response schema in the contract.
8. **`Rate limited` (`RATE_LIMITED`, warning)** → the gateway/API kept answering `429 Too Many Requests` after `--maxRateLimitRetries` (or returned a `Retry-After` exceeding `--maxRetryAfterInSec`), so the test verdict is unknown. **This is a transport/throttling outcome, not an API defect** (and is never emitted as a WFC fault). Re-run the affected tests with `cats replay --warnings -r cats-report -s <server_url>` or lower the request rate with `--maxRequestsPerMinute`.
9. **HTTP-semantics and behavior oracles** (usually classified `CONTRACT_MISMATCH`; most are warnings, which are triaged by default). These are server behavior bugs: never fix them with `contract-patch.yml`, `--refData` or `--correlations`.
   - `Repeated PUT returned 201 Created` (`PutIdempotency`, error) → the PUT handler reports "created" for an existing resource. Return `200`/`204` on update (RFC 9110 §9.3.4).
   - `PUT on existing resource returned 201 Created` (`MisleadingCreatePut`, error) → a `GET` immediately before the `PUT` returned `2XX` proving the resource exists, yet the `PUT` returned `201 Created` instead of `200`/`204` (or the `GET` handler wrongly returns `200` for missing resources).
   - `PUT did not perform a full replacement` (`PartialUpdatePut`, warning) → `PUT` must replace the entire representation (RFC 9110 §9.3.4), not merge fields like `PATCH`. Either an omitted optional field retained its previous value or a shared writable field differed on the subsequent `GET`.
   - `Rejected update partially applied` (`RejectedUpdateSideEffects`; error for PATCH, warning for PUT) → the request was rejected with `4XX`, yet the canary value `cats-atomic-patch`/`cats-atomic-put` it carried was persisted. Validate the whole body before saving, or wrap the update in one transaction.
   - `Filter parameter ignored` (`FilterParameterHonored`) → items not matching `?field=value` were returned. Check that the filter is applied in the query. Treat it as a data-exposure issue when the parameter scopes ownership (`ownerId`, `tenantId`).
   - `OPTIONS Allow header not matching contract` (`OptionsAllowConformance`) → the message lists undocumented and missing methods. Undocumented verbs may be unintended endpoints, so disable them or document them. Missing verbs usually mean a framework-generated `OPTIONS` that doesn't know the routes. `HEAD`/`OPTIONS` are ignored.
10. **Injection and SSRF findings** (`*InjectionInStringFields`, `PathTraversalInStringFields`, `SSRFInUrlFields`) carry a `confidence` (shown in the test case report and JSON, and at the end of the result details). CATS strips echoes of the payload before looking for evidence, compares against a fresh unfuzzed baseline response of the same operation (falling back to the latest recorded `2XX` exchange), and verifies reproducibility on replay:
   - `CONFIRMED` (error) → strong evidence (file contents, command output, computed arithmetic/expression canaries, `UNION` output, linearly correlated high/low sleep delays over a `< 2s` baseline, or reproduced boolean-differential true/false pairs) absent from the baseline and reproduced. Treat it as a real vulnerability and fix it first.
   - `FIRM` (error) → reproduced, but either no baseline was available or the evidence is heuristic (error keywords, scores). Verify manually with `cats replay <test>`.
   - `TENTATIVE` (warning; error only when the response is `5XX`) → unverified heuristic, a plain reflection (stored XSS, echoed SSRF URL), or evidence that did not reproduce. Review it, but expect false positives. It is not emitted as a WFC fault.
   - A finding whose evidence also appears in the baseline response is dropped, because the payload did not cause it.

---

## 2. Verify Fixes Live in Seconds (`cats triage --verify`)

After fixing a server bug or updating headers, verify all problem occurrences against the live server without re-running the full fuzzer suite:
```bash
export Authorization='Bearer eyJ...'
cats triage -r cats-report --verify -s <server_url> -j
```
- Reports mask sensitive headers and query params as **`$$<HeaderNameWithOnlyAlphanumerics>`** (e.g. `Authorization` → `$$Authorization`, `X-API-Key` → `$$XAPIKey`), whatever env var you originally used. Export a variable with exactly that name, or override the header with `-H 'Authorization=Bearer eyJ...'`.
- Replays **1 representative request per occurrence** as-is (no upstream producers are re-run, so IDs may be stale).
- Marks each occurrence with `verificationStatus` and records `verifiedStatusCode` / `verificationNote`:
  - **`FIXED`**: `SERVER_CRASH_5XX` now returns `< 500`; other occurrences return a code allowed by the test's `expectedResult` (e.g. `[400, 422]`, `4XX`).
  - **`STILL_FAILING`**: the original failure still reproduces.
  - **`INCONCLUSIVE`**: the replay cannot prove anything: a masked variable is missing, the request could not be sent, an unrelated `401`/`403` came back, a `404` came back for a test that originally got something else (stale IDs; use `cats replay TestN --stateful`), or the occurrence is contract-only drift.
- Returns exit code `0` if every occurrence of a `SERVER_ERROR` problem is `FIXED`, or `1` if any is `STILL_FAILING` or `INCONCLUSIVE`.

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
- `-b/--baseline` and `-t/--target` accept a report folder or a `cats-summary-report.json` file.
- Both summaries must have `problemKeyVersion: 1` and persisted `problems`. Missing or other versions exit `2` with: `<file> was written by a CATS version without problems (problemKeyVersion N, expected 1); re-run it with this CATS version`. No recomputation, count mode, `--compareBy` or `--compareScope`.
- Identity is `(problemId, operation)`; `ACCEPTED_REQUEST` also includes each mutated field (empty string when there are no fields).
- States: `NEW` (target only), `FIXED` (baseline only), `UNCHANGED` (matched by `ID` or `PATTERN`), `NOT_COMPARABLE` (all occurrence fuzzers absent from the other run's `testCases[].fuzzer`, including successful tests).
- Unmatched `SERVER_ERROR` and `ERROR_MESSAGE` occurrences can match a server-message pattern: equal whitespace-token counts, with each token equal or `<*>` on either side. Matching is one-to-one, within the same kind, operation and statuses; `ERROR_MESSAGE` also requires the same verdict family.
- Exit codes: `0` no regression, `1` a `NEW` error occurrence (also a warning with `--failOnNewWarnings`), `2` invalid input. Changed test counts are informational and never fail the gate. `NOT_COMPARABLE` never fails it.
- Console sections: `New`, `Fixed`, `Not comparable`, `Unchanged (count changed)`. Each line names the state, problem id, operation, optional field, title and baseline/target test counts.
- JSON (`-j`): `{baseline, target, problemKeyVersion, regression, newOccurrences, fixedOccurrences, unchangedOccurrences, notComparable, matchedByPattern}`. Each occurrence has `{state, problemId, kind, level, title, operation, field, statuses, baselineTests, targetTests, matchedBy}`; `matchedBy` is `ID`, `PATTERN`, or `null` for unmatched occurrences. `matchedByPattern` is the number of pattern pairs.
- To gate during fuzzing, pass `--baseline <report>` and optional `--failOnNewWarnings` to the root `cats` command. The gate and exporters share the same prepared problems.
- `FIXED` means absent in this run under these comparison rules; confirm the operation and input were exercised before treating it as a verified fix.
