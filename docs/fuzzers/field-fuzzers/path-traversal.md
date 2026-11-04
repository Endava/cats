---
hide_table_of_contents: true
---

# Path Traversal In String Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `PathTraversalInStringFieldsFuzzer` |
| **Log Key** | **PTISFF** |
| **Description** | Sends relative and URL-encoded Path Traversal / Local File Inclusion payloads (`../../../../etc/passwd`, `..\\..\\..\\..\\windows\\win.ini`, `%2e%2e%2f...`, null-byte suffixes, etc.) in string fields and inspects responses for file content disclosure or unmasked filesystem exception leaks. |
| **Enabled by default?** | Yes (included in `security`, `ci`, and `full` profiles) |
| **Target field types** | String fields |
| **Expected result when fuzzed field is required** | `4XX` or sanitized `2XX` without file disclosure or filesystem error leaks |
| **Expected result when fuzzed field is optional** | `4XX` or sanitized `2XX` without file disclosure or filesystem error leaks |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` or sanitized `2XX` |
| **Fuzzing logic** | Prioritizes path/file-like string fields (and tests up to 5 string fields per request by default, or all with `--includeAllInjectionPayloads`) using curated Unix/Windows traversal payloads. |
| **Conditions when this fuzzer will be skipped** | Non-string fields, `discriminator` fields, `enum` fields, or fields in `--refData`. |
| **HTTP methods that will be skipped** | None |
| **Reporting** | Reports `error` (`Path traversal / file disclosure vulnerability detected`) if the response contains `/etc/passwd`, `win.ini`, `/proc/self/environ`, or unmasked `FileNotFoundException`/`NoSuchFileException`/`AccessDeniedException`/`ENOENT` indicators, or if the server returns `5XX`. |
