---
sidebar_position: 14
description: Exit Codes
---

# Exit Codes
CATS has the following exit codes:

- `0` when execution completes successfully and the configured quality gate passes
- `1` when an internal execution error occurs or the configured `--failOn`/`--qualityGate` condition fails
- `2` when command-line input is invalid
- `130` when an interactive TUI run is cancelled

:::info
The number of errors and warnings is still recorded in
`cats-report/cats-summary-report.json`, even when a quality gate changes the
process exit status:

```bash
jq '{errors, warnings, qualityGatePassed}' cats-report/cats-summary-report.json
```
:::
