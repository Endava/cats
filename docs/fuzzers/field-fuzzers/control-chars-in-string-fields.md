---
hide_table_of_contents: true
---

# Control Chars In String Fields

This fuzzer has 2 flavours depending on `--sanitizationStrategy`. Unlike the opt-in `--includeControlChars` fuzzers, `ControlCharsInStringFields` is enabled by default in `security`, `ci`, `crash`, and `full` profiles and sends a compact, high-impact set of control characters (`\u0000` NUL byte, `\u0007` BEL, `\u0008` BS, `\u001B` ESC, and `\uFFFE`).

## Control Chars In String Fields SANITIZE_AND_VALIDATE

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ControlCharsInStringFieldsSanitizeValidateFuzzer` |
| **Log Key** | **CCISFSV** |
| **Description** | Inserts a compact set of high-impact control characters (including NUL bytes) into string fields when `--sanitizationStrategy=SANITIZE_AND_VALIDATE` (default). |
| **Enabled by default?** | Yes |
| **Target field types** | String fields |
| **Expected result when fuzzed field is required** | `2XX` |
| **Expected result when fuzzed field is optional** | `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `2XX` |
| **Fuzzing logic** | Inserts compact control characters inside valid string field values. |
| **Conditions when this fuzzer will be skipped** | Non-string fields, `discriminator` fields, `enum` fields, or fields in `--refData`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Standard CATS reporting against the expected `2XX` response family. |

## Control Chars In String Fields VALIDATE_AND_SANITIZE

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ControlCharsInStringFieldsValidateSanitizeFuzzer` |
| **Log Key** | **CCISFVS** |
| **Description** | Inserts a compact set of high-impact control characters (including NUL bytes) into string fields when `--sanitizationStrategy=VALIDATE_AND_SANITIZE`, expecting `4XX`. |
| **Enabled by default?** | Active when `--sanitizationStrategy=VALIDATE_AND_SANITIZE` |
| **Target field types** | String fields |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Inserts compact control characters inside valid string field values. |
| **Conditions when this fuzzer will be skipped** | Non-string fields, `discriminator` fields, `enum` fields, or fields in `--refData`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Standard CATS reporting against the expected `4XX` response family. |
