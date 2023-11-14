---
sidebar_position: 14
description: Exit Codes
---

# Exit Codes
CATS has the following exit codes:
- `191` when for invalid input
- `192` when something unexpected happens during execution
- `number_of_errors` number of errors found while fuzzing

:::info
There might be cases when `191`, `192` might overlap with the actual number of errors reported by CATS.
These are probably very rare cases. You can use the `cat cats-report/cats-summary-report.json | jq .errors` to differentiate if needed.
:::
