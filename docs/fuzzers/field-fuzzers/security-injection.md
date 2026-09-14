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
