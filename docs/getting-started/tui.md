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

The overview includes paths, run configuration, response time, response-code
counts, result counts, and the fuzzers that ran. Use `j`/`k` or the arrow keys
to select a test and press Enter to inspect its request, response, result,
trace, and replay command.

Useful keys include:

- `1` overview
- `2` execution details
- `3` execution summary and quality-gate result
- `4` details grouped by result reason
- `5` included paths
- `6` tests sorted by response time
- `/` search; `a`, `e`, `w`, `s`, and `i` filter all, errors, warnings, successes, and skipped results
- `q` quit

The TUI requires an interactive terminal at least 80 columns by 24 rows and
cannot be combined with `--dryRun`. It is available for OpenAPI-backed
fuzzing; standalone `cats template` uses normal CLI output. The interface
retains the most recent 10,000 test details by default. Change this with
`--tuiMaxResults`; aggregate statistics still cover the complete run.

Pressing `q` during an active run requests cancellation and exits with status
`130` after preserving results already written.
