---
sidebar_position: 5
description: How to supply dynamic values in CATS configuration
---

# Dynamic Values

You can supply dynamic values in [`FunctionalFlowFuzzer`](../fuzzers/special-fuzzers/functional-fuzzer) files, [`SecurityFuzzer`](../fuzzers/special-fuzzers/security-fuzzer) files, `--correlations` files, `--refData` files, and `--headers` files using either **shorthand generators (`#(...)`)** or **SpEL-style expressions (`T(...)`)**.

## Shorthand generators (`#(...)`)

Shorthand functions use the `#(...)` syntax and can be used standalone, embedded inside larger strings, or composed with one another:

```yaml
name: "Pet-#(alphanumeric(8))"
invoiceCode: "INV-#(upper(alpha(3)))-#(numeric(4))"
tokenHex: "#(upper(hex(16)))"
currency: "#(pick(USD,EUR,GBP))"
email: "#(email)"
identifier: "#(uuid)"
sortableId: "#(ulid)"
country: "#(countryCode)"
isActive: "#(bool)"
createdOn: "#(today)"
futureDate: "#(todayPlus(30))"
expiresAt: "#(nowPlusHours(2))"
epochSeconds: "#(epoch)"
signature: "#(sha256(cats))"
quantity: "#(randomInt(1,10))"
```

### Identifiers, Strings, Numbers & Booleans
- `#(uuid)` — RFC 4122 UUID (seeded when `--seed` is set)
- `#(ulid)` — 26-character lexicographically sortable ULID
- `#(email)` — random valid `@cats.io` email address
- `#(country)` or `#(countryCode)` — random ISO 3166-1 alpha-2 country code (e.g. `US`, `RO`, `DE`)
- `#(alphanumeric)`, `#(alphabetic)` (or `#(alpha)`), `#(numeric)`, `#(ascii)`, `#(hex)` — random string of default length `12`, or pass `(length)` / `(min,max)` (for example `#(alphanumeric(16))`, `#(numeric(4,8))`, or `#(hex(24))`)
- `#(upper)` (or `#(uppercase)`) and `#(lower)` (or `#(lowercase)`) — when called without arguments, generate a 12-character random uppercase (`[A-Z]{12}`) or lowercase (`[a-z]{12}`) alphabetic string
- `#(randomInt(max))` or `#(randomInt(min,max))` — random integer in `[0, max)` or `[min, max)`
- `#(bool)` (aliases `#(boolean)`, `#(randomBoolean)`) — random boolean (`true` or `false`)
- `#(pick(v1,v2,...))` or `#(oneOf(v1,v2,...))` — picks one value at random from comma-separated candidates (supports quoted literals, context variables, and nested shorthand functions such as `#(pick(upper(alpha(4)),numeric(4)))`)

### Dates, Timestamps & Epoch
- `#(now)` — current UTC `OffsetDateTime` (`ISO-8601`)
- `#(nowPlusDays(days))`, `#(nowMinusDays(days))`, `#(nowPlusHours(hours))`, `#(nowMinusHours(hours))` — UTC `OffsetDateTime` offset from now
- `#(today)` — current UTC `LocalDate` (`YYYY-MM-DD`)
- `#(todayPlus(days))`, `#(todayMinus(days))` — `LocalDate` offset by `days`
- `#(epoch)` (aliases `#(epochSeconds)`, `#(timestamp)`) — current Unix epoch timestamp in seconds
- `#(epochMillis)` (alias `#(timestampMillis)`) — current Unix epoch timestamp in milliseconds

### Composable String, Encoding & Hashing Helpers
String and encoding helpers accept a literal, a context variable name, an embedded template (`pre-#(alpha(4))-post`), or a nested shorthand function:
- `#(upper(expr))` / `#(uppercase(expr))` — converts the resolved value to uppercase (e.g. `#(upper(alphabetic))`, `#(upper(alpha(6)))`, `#(upper(uuid))`)
- `#(lower(expr))` / `#(lowercase(expr))` — converts the resolved value to lowercase (e.g. `#(lower(alphanumeric(10)))`)
- `#(capitalize(expr))` — capitalizes the first character and lowercases the remainder (e.g. `#(capitalize(alphabetic(6)))`)
- `#(trim(expr))` — trims leading and trailing whitespace
- `#(reverse(expr))` — reverses the resolved string
- `#(length(expr))` / `#(len(expr))` — returns the character length of the resolved value
- `#(base64(expr))` — standard UTF-8 Base64 encoding
- `#(base64Url(expr))` — URL-safe UTF-8 Base64 encoding without padding
- `#(sha256(expr))` — lowercase hexadecimal SHA-256 digest (can be combined with `upper`, e.g. `#(upper(sha256(cats)))`)
- `#(md5(expr))` — lowercase hexadecimal MD5 digest
- `#(urlEncode(expr))` — UTF-8 URL encoding

### Environment Variables (`$$ENV_VAR`)
- `$$ENV_VAR` — reads `ENV_VAR` from the process environment (or `--envFile` / `./.env`) and automatically masks its value in console output and HTML/JSON reports. Note that `$$ENV_VAR` resolves only when it is the **entire** value (for example `"$$API_AUTH"` with `export API_AUTH='Bearer eyJ...'`, not `"Bearer $$API_TOKEN"`). Always single-quote `$$ENV_VAR` in shell commands (`-H 'Authorization=$$API_AUTH'`) so bash does not expand `$$` to its process ID.

## SpEL expressions (`T(...)`)

For custom date arithmetic or string manipulation, CATS supports `T(...)` expressions for `java.time.*` and `org.apache.commons.lang3.*`.

For example, to set a field to 10 days from now (`T(java.time.OffsetDateTime).now().plusDays(10)`) and assert in `expect.that:` that a returned `expiry` date is after today:

```yaml
steps:
  - name: "createSubscription"
    description: "Create a subscription expiring in 10 days"
    POST: "/subscriptions"
    body:
      plan: "PRO"
      billing.expiresAt: "T(java.time.OffsetDateTime).now().plusDays(10)"
    save:
      - subName: "name"
    expect:
      status: 201
      that:
        - "T(java.time.LocalDate).now().isBefore(T(java.time.LocalDate).parse(expiry.toString()))"
```

Dynamic expressions can also reference variables saved in earlier steps (`${subName}`), request fields (`${request.plan}` / `request.plan`), and response fields.
