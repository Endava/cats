---
hide_table_of_contents: true
---

# ReadOnly Fields Mass Assignment

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ReadOnlyFieldsMassAssignmentFuzzer` |
| **Log Key** | **ROFMAF** |
| **Description** | Re-injects properties marked `readOnly: true` in the OpenAPI schema into write requests (`POST`, `PUT`, `PATCH`) with canary values to detect OWASP API3:2023 Broken Object Property Level Authorization (mass assignment of server-managed properties such as `id`, `role`, `createdAt`, or `status`). |
| **Enabled by default?** | Yes (included in `security`, `compliance`, `ci`, `crash`, and `full` profiles) |
| **Target field types** | Schema properties declared with `readOnly: true` |
| **Expected result when fuzzed field is required** | `4XX` (or `2XX` where the server ignores the `readOnly` input without reflecting the canary value) |
| **Expected result when fuzzed field is optional** | `4XX` (or `2XX` where the server ignores the `readOnly` input without reflecting the canary value) |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Because CATS strips `readOnly: true` properties from normal write payloads, this fuzzer re-injects each `readOnly` property one at a time with a canary value (`cats-readonly-tamper`, `987654`, or `true`) and checks whether the server rejects it or reflects the tampered value in the `2XX` response. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty or the request schema declares no `readOnly: true` properties. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` (`Mass Assignment vulnerability detected`) if a `2XX` response reflects the injected `readOnly` canary value, or `Server error` on `5XX`; reports `info` when rejected with `4XX` or ignored without reflection. |
