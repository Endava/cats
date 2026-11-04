---
hide_table_of_contents: true
---

# Emojis In String Fields

This fuzzer has 2 flavours depending on `--sanitizationStrategy`. Unlike the opt-in `--includeEmojis` fuzzers, `EmojisInStringFields` is enabled by default in `security`, `ci`, `crash`, and `full` profiles and sends a compact set of 4-byte supplementary plane and ZWJ emojis to expose UTF-8 / `utf8mb3` encoding crashes.

## Emojis In String Fields SANITIZE_AND_VALIDATE

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `EmojisInStringFieldsSanitizeValidateFuzzer` |
| **Log Key** | **EISFSV** |
| **Description** | Sends 4-byte supplementary plane and ZWJ sequence emojis in fields when `--sanitizationStrategy=SANITIZE_AND_VALIDATE` (default). |
| **Enabled by default?** | Yes |
| **Target field types** | String fields |
| **Expected result when fuzzed field is required** | `2XX` |
| **Expected result when fuzzed field is optional** | `2XX` |
| **Expected result when fuzzed value is not matching field pattern** | `2XX` |
| **Fuzzing logic** | Inserts 4-byte supplementary plane emojis into field values while respecting length constraints. |
| **Conditions when this fuzzer will be skipped** | When field is a `discriminator`, an `enum`, or present in `--refData`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Standard CATS reporting against the expected `2XX` response family. |

## Emojis In String Fields VALIDATE_AND_SANITIZE

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `EmojisInStringFieldsValidateSanitizeFuzzer` |
| **Log Key** | **EISFVS** |
| **Description** | Sends 4-byte supplementary plane and ZWJ sequence emojis in fields when `--sanitizationStrategy=VALIDATE_AND_SANITIZE`, expecting `4XX`. |
| **Enabled by default?** | Active when `--sanitizationStrategy=VALIDATE_AND_SANITIZE` |
| **Target field types** | String fields |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Inserts 4-byte supplementary plane emojis into field values. |
| **Conditions when this fuzzer will be skipped** | When field is a `discriminator`, an `enum`, or present in `--refData`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Standard CATS reporting against the expected `4XX` response family. |
