---
hide_table_of_contents: true
---

# Sensitive Data Exposure

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `SensitiveDataExposureFuzzer` |
| **Log Key** | N/A; the fuzzer name and scenario identify the test. |
| **Description** | Sends a happy-path request and checks that the `2XX` response body does not expose fields marked `writeOnly: true` in the OpenAPI contract or undeclared fields with sensitive names (such as `password`, `passwd`, `passphrase`, `passwordHash`, `secret`, `secretKey`, `privateKey`, `apiKey`, `accessToken`, `refreshToken`, `ssn`, `cvv`, or `cvc`) containing unmasked values. |
| **Enabled by default?** | Yes; included in the `security` and `full` profiles. |
| **Expected result** | `2XX` with no `writeOnly` or unmasked undeclared sensitive fields in the response body. |
| **Fuzzing logic** | Sends a populated request and inspects the returned JSON response against the request and response schemas (including `$ref` and `allOf`/`oneOf`/`anyOf` compositions). |
| **Conditions when this fuzzer will be skipped** | None beyond standard HTTP method filtering. |
| **HTTP methods that will be skipped** | `DELETE`, `HEAD`, `TRACE`. |
| **Reporting** | Reports `error` (`WriteOnly fields exposed in response`) if any contract `writeOnly` field is returned with a non-null unmasked value, `warn` (`Sensitive data exposed in response`) if undeclared sensitive fields with unmasked values are present, and normal `2XX` reporting otherwise. |
