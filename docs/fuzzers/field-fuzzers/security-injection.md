---
sidebar_position: 94
description: Additional server-side injection fuzzers
---

# Additional Injection Fuzzers

CATS includes these injection checks in addition to SQL, NoSQL, command, XSS,
and SSRF fuzzers:

- `LdapInjectionInStringFields` sends LDAP injection payloads to string fields to detect authentication bypass or unauthorized data access.
- `SstiInjectionInStringFields` sends server-side template injection payloads.
- `XxeInjectionInStringFields` sends XML External Entity payloads.

They are included in the `security` and `compliance` profiles. Select them by
name or include all security fuzzers with `--profile security`.

| Full Fuzzer Name | Log Key | Enabled by default? | Target field types | Expected result | Fuzzing logic | Conditions when skipped | HTTP methods skipped | Reporting |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| `LdapInjectionInStringFieldsFuzzer` | N/A | Yes in the `security`, `compliance`, and `full` profiles | String fields, with priority for identity and filter-like names | `4XX` unless the API intentionally sanitizes and accepts the value | Sends LDAP injection payloads to detect authentication bypass or unauthorized data access. | Non-string fields, discriminators, enums, and reference data. | None | Reports injection evidence, unexpected success, server errors, and other unexpected responses according to normal CATS reporting. |
| `SstiInjectionInStringFieldsFuzzer` | N/A | Yes in the `security`, `compliance`, and `full` profiles | String fields | `4XX` unless the API intentionally sanitizes and accepts the value | Sends server-side template injection payloads and checks for evidence of template evaluation. | Non-string fields, discriminators, enums, and reference data. | None | Reports template-evaluation evidence, unexpected success, server errors, and other unexpected responses according to normal CATS reporting. |
| `XxeInjectionInStringFieldsFuzzer` | N/A | Yes in the `security`, `compliance`, and `full` profiles | String fields that can carry XML or entity data | `4XX` unless the API intentionally sanitizes and accepts the value | Sends XML External Entity payloads and checks for evidence of unsafe entity processing. | Non-string fields, discriminators, enums, and reference data. | None | Reports XXE evidence, unexpected success, server errors, and other unexpected responses according to normal CATS reporting. |
