---
sidebar_position: 1.5
description: Breaking changes and migration steps for CATS 16.0.0
---

# Upgrading to CATS 16.0.0

CATS 16.0.0 changes how HTML reports group findings, how triage and CI compare
them, and which findings blackbox mode retains. Update report jobs and regenerate
your baselines when you upgrade from CATS 15.x.

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

- Remove `--include-warnings`. Triage includes warnings by default. Add
  `--errors-only` to keep the previous error-only view.
- Migrate consumers of `cats triage -j` from `clusters` to
  `problems[].occurrences[]`. Live verification now replays one representative
  request per occurrence. Keep the referenced `Test*.json` files with the summary.
- Add `--show-reproducers` if your workflow expects full curl commands in console
  output. JSON output and `--export-reproducers` continue to include them.
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
report evidence.
