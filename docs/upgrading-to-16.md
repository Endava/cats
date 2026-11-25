---
sidebar_position: 1.5
description: Breaking changes and migration steps for CATS 16.0.0
---

# Upgrading to CATS 16.0.0

CATS 16.0.0 changes how HTML reports group findings, how triage and CI compare
them, which findings blackbox mode retains, and removes deprecated 15.x legacy
formats and flags while modernizing CLI defaults. Update report jobs and
regenerate your baselines when you upgrade from CATS 15.x.

## HTML reports and summary files

- Replace `--reportFormat HTML_JS_CLUSTERS` with `--reportFormat HTML_JS`, or use
  `HTML_ONLY` for a static report. Both formats include a **Problems** view
  alongside **All tests**. If your format list already contains `HTML_JS`, remove
  the `HTML_JS_CLUSTERS` entry.
- Update report integrations that load `error-templates.js`. HTML_JS test pages
  now share problem metadata through `problems.js`. Use
  `cats-summary-report.json` for automation. HTML_JS renders the test table
  progressively.
- Summaries persist `problems`, the `problemId` on associated test records, and
  `problemKeyVersion: 1`. Regenerate old reports with 16.0.0 before triage or
  baseline comparison. CATS uses the identities stored in the reports, so
  regenerate both sides when comparing builds that used older problem
  normalization, including development builds that grouped echoed root JSON
  strings differently.

See [Interpreting Results](./getting-started/interpreting-results.md).

## Triage

- Triage now focuses on **error** problems by default. Pass `-w` or
  `--include-warnings` (`--includeWarnings`) to include warning problems too.
- Migrate consumers of `cats triage -j` from `clusters` to
  `problems[].occurrences[]`, and from `noiseReductionPercent` to
  `groupingReductionPercent`. Live verification now replays one representative
  request per occurrence. Keep the referenced `Test*.json` files with the summary.
- Add `--show-reproducers` (`--showReproducers`) if your workflow expects full
  curl commands in console output. JSON output and `--export-reproducers`
  continue to include them.
- Triage returns exit code `2` for an incompatible summary or missing
  referenced test evidence.

## Baseline gates and `cats compare`

- Remove `--compareScope` and `--failOnNewErrors`. A new error occurrence always
  fails comparison. Use `--failOnNewWarnings` to gate on new warnings as well.
- Replace count and scope fields in `cats compare -j` consumers with
  `newOccurrences`, `fixedOccurrences`, `unchangedOccurrences`, and
  `notComparable`. The output also contains `baseline`, `target`,
  `problemKeyVersion`, `regression`, and `matchedByPattern`.
- Review the first comparison after regenerating the baseline. The gate uses
  problem occurrences keyed by problem and operation, with separate occurrences
  per mutated field for accepted requests. Changes in the number of tests alone
  are informational. An incompatible baseline returns exit code `2`.

See [Quality Gates and Stop Conditions](./getting-started/quality-gates.md#baseline-regression-gating---baseline-and-cats-compare)
for comparison states and exit codes.

## Functional flows and removal of legacy `FunctionalFuzzer` / `SecurityFuzzer` YAML

- The deprecated path-grouped `FunctionalFuzzer` and `SecurityFuzzer` YAML
  formats (`cats-global-vars`, top-level `/path:` or `all:` keys, `httpMethod`,
  `expectedResponseCode`, `output`, `verify`, `checkBoolean`, `targetFields`,
  `targetFieldTypes`, `stringsFile`) have been removed. Migrate functional and
  custom security test files to the sequential `steps:` `FunctionalFlowFuzzer`
  format (`cats run <flow.yml>`, `--functionalFlow <flow.yml>`, or
  `--securityFuzzerFile <flow.yml>`).
- `cats run` now applies `--refData` (`-R`) and `--correlations` as request
  inputs before step-level `body:`, `omit:`, `path:`, `query:`, and `correlate:`
  overrides, and replaces `--createRefData` (`refData_custom.yml`) and
  `*_replaced.yml` templates with `--export-refdata <file>` (`--exportRefData <file>`)
  to export variables captured by `save:`.

See [Functional Flow Fuzzer](./fuzzers/special-fuzzers/functional-fuzzer.md#migrating-from-the-legacy-functionalfuzzer-syntax-removed-in-1600)
and [Security Fuzzer](./fuzzers/special-fuzzers/security-fuzzer.md#migrating-from-the-legacy-securityfuzzer-syntax-removed-in-1600).

## CLI options and default value changes

- **Removed `--filterResponse*` flags**: Replace `--filterResponseCodes` (`--fc`),
  `--filterResponseSize` (`--fs`), `--filterResponseWords` (`--fw`),
  `--filterResponseLines` (`--fl`), and `--filterResponseRegex` (`--fr`) with the
  corresponding `--ignoreResponse*` (`-i`, `--is`, `--iw`, `--il`, `--ir`) option
  combined with `-k` (`--skipReportingForIgnoredCodes`).
- **Removed `--healthCheck`**: Use `cats smoke` for a pre-flight endpoint probe
  or `--profile health-check` to run only `HappyPathFuzzer`.
- **`--allowDifferentCaseEnumValues` now defaults to `false`**:
  `EnumCaseVariantFieldsFuzzer` expects `4XX` by default (matching OpenAPI case
  sensitivity and `--strictTypes`). Pass `--allowDifferentCaseEnumValues` if
  your API intentionally accepts case-insensitive enum values.
- **`--useRequestBodyExamples` and `--useSchemaExamples` now default to `true`**:
  All four OpenAPI example sources (request body, response body, schema, and
  property examples) are enabled by default and can be disabled together with
  `--no-useExamples` or individually with `--no-useRequestBodyExamples`,
  `--no-useResponseBodyExamples`, `--no-useSchemaExamples`, and
  `--no-usePropertyExamples`.
- **`--checkUpdate` now defaults to `false`**: Pass `--checkUpdate` explicitly to
  enable startup version checks.
- **`cats generate` `--selfReferenceDepth` (`-L`) now defaults to `2`**: Matches
  the fuzzing default so `cats generate` and `cats` produce identical payloads
  for the same `--seed`.
- **`--headers` vs. `-H` (`--header`)**: `-H` (`--header`) passes inline
  `name=value` headers across all commands (including `cats template` and
  `cats replay`), while `--headers=FILE` loads a YAML headers file. Inline
  parameter flags also provide long aliases (`-Q` / `--queryParam`,
  `-R` / `--refDataParam`, `-P` / `--urlParam`).
- **`--skipFields` skips across all fuzzers by default**: `--skipFields` now
  excludes the specified fields (and their nested subfields) across all fuzzers
  without requiring a leading `!` prefix, and supports both dot
  (`shippingAddress.country`) and `#` (`shippingAddress#country`) notation.
- **Unanchored `--matchResponseRegex` (`--mr`) and `--ignoreResponseRegex` (`--ir`)**:
  Response regex matching now searches anywhere in the response body (`.find()`)
  instead of requiring a full-body match (`.matches()`). Use `^...$` when you
  want to anchor to the entire response body.

## Configuration, correlations, and dynamic values

- **Unified `.` and `#` field paths and HTTP method scoping**: `refData.yml`,
  `headers.yml`, and `correlations.yml` all support exact paths, wildcard
  prefixes (`/orders*`), and HTTP method scoping (`"POST /orders"`,
  `"PUT /orders*"`). Both dot-notation (`shippingAddress.country`) and
  `#`-notation (`shippingAddress#country`) in `refData.yml` are normalized and
  respected by boundary fuzzers and `RuntimeResourcePool`.
- **Correlation evaluation order and `!=` on absent fields**: Required-field
  analysis for `correlations.yml` now runs after static `--refData` overrides are
  applied, and `!=` evaluates to `false` when the left-hand field is absent from
  the payload (use `is null` to test absence).
- **Embedded `$$ENV_VAR` and typed `#(...)` expressions**: `$$ENV_VAR` now
  resolves both as a standalone value (`"$$API_AUTH"`) and embedded inside
  strings (`"Bearer $$API_TOKEN"`). Standalone `#(...)` and `T(...)` expressions
  automatically preserve boolean and numeric JSON types across `refData.yml`,
  `correlations.yml`, and `flows.yml`.

## Blackbox mode

`--blackbox` (`-b`) now retains `5XX` server errors except `501` and
high-severity security findings with explicit `CONFIRMED` confidence. A
confirmed finding can trigger your CI gate even if the HTTP status is `2XX`.
`FIRM`, `TENTATIVE`, unassessed, and keyword-only security findings are
informational in blackbox mode, including under `--strict`.

- For the previous status-code filter, replace `-b` with `-i 2xx,4xx,501`.
- Explicit ignores still apply. For example, `-b -i 2xx` suppresses confirmed
  findings returned with a `2XX` response.
- Use the ordinary reporting mode and retain successful transactions when
  collecting evidence for `cats triage --export-refdata`.

See [Blackbox Mode](./getting-started/running-cats.md#blackbox-mode).

## WFC exports

Update category mappings and review changes in distinct-fault totals. These dedicated CATS categories apply
when the pinned WFC catalog has no corresponding category.

| Finding | Previous category | 16.0.0 category |
| --- | --- | --- |
| Server-side template injection | `907` | `913 CATS_SSTI` |
| XML external entity injection | `907` | `914 CATS_XXE` |
| Path traversal | `309` | `915 CATS_PATH_TRAVERSAL` |

For OpenAPI runs, WFC endpoint IDs and coverage now use declared operations.
Eligible faults from a synthetic HTTP method can be attributed to a declared
operation on the same path. The underlying test is still available in the
report evidence. HTTP 500 fault contexts also mask echoed root-primitive JSON
string inputs as `<INPUT>` consistently with structured JSON payloads.
