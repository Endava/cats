---
sidebar_position: 21
description: Use the interactive terminal interface for CATS runs
---

# Terminal Interface

Run an OpenAPI fuzzing command with `--tui` to follow progress and inspect
results interactively:

```bash
cats -c openapi.yml -s http://localhost:8080 --tui
```

The overview includes live path/wave progress, run configuration, response time,
response-code counts, result counts, and the fuzzers that ran. Use `j`/`k` or
the arrow keys to select a fuzzer, reason, path, or test and press `Enter` to
drill down or inspect the full-screen test detail view (including request,
response, result, runtime correlations, and equivalent `curl` command).

Useful keys include:

- `1` overview (press `Enter` on a fuzzer row to view its retained tests)
- `2` execution details (test list)
- `3` execution summary and quality-gate result (press `Enter` on a fuzzer row to view its retained tests)
- `4` details grouped by result reason (press `Enter` to view matching tests)
- `5` included paths (press `Enter` to view matching tests)
- `6` tests sorted by response time
- `/` search; `a`, `e`, `w`, `s`, and `i` filter all, errors, warnings, successes, and skipped results
- `c` copy the selected test's equivalent `curl` command to the OS clipboard
- `n` / `p` jump to the next or previous test while inside the full-screen test detail view
- `?` open or close the keyboard shortcuts & navigation help overlay
- `q` quit (or cancel an active run)

The TUI requires an interactive terminal at least 80 columns by 24 rows and
cannot be combined with `--dryRun`. It is available for OpenAPI-backed
fuzzing; standalone `cats template` uses normal CLI output. The interface
retains the most recent 10,000 test details by default. Change this with
`--tuiMaxResults`; aggregate statistics still cover the complete run.

Pressing `q` during an active run requests cancellation and exits with status
`130` after preserving results already written.
