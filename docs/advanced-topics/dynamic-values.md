---
sidebar_position: 5
description: How to supply dynamic values in CATS configuration
---

# Dynamic Values

You can supply dynamic values in [`FunctionalFlowFuzzer`](../fuzzers/special-fuzzers/functional-fuzzer) files, [`SecurityFuzzer`](../fuzzers/special-fuzzers/security-fuzzer) files, `--correlations` files, `--refData` files, and `--headers` files using either **shorthand generators (`#(...)`)** or **SpEL-style expressions (`T(...)`)**.

## Shorthand generators (`#(...)`)

Shorthand functions use the `#(...)` syntax and can be used standalone or embedded inside larger strings:

```yaml
name: "Pet-#(alphanumeric(8))"
code: "#(alphanumeric)"
email: "#(email)"
identifier: "#(uuid)"
sortableId: "#(ulid)"
country: "#(countryCode)"
createdOn: "#(today)"
futureDate: "#(todayPlus(30))"
expiresAt: "#(nowPlusHours(2))"
quantity: "#(randomInt(1,10))"
```

Supported shorthand functions:
- `#(uuid)` — RFC 4122 UUID (seeded when `--seed` is set)
- `#(ulid)` — 26-character lexicographically sortable ULID
- `#(email)` — random valid `@cats.io` email address
- `#(country)` or `#(countryCode)` — random ISO 3166-1 alpha-2 country code (e.g. `US`, `RO`, `DE`)
- `#(now)` — current UTC `OffsetDateTime` (`ISO-8601`)
- `#(nowPlusDays(days))`, `#(nowMinusDays(days))`, `#(nowPlusHours(hours))`, `#(nowMinusHours(hours))` — UTC `OffsetDateTime` offset from now
- `#(today)` — current UTC `LocalDate` (`YYYY-MM-DD`)
- `#(todayPlus(days))`, `#(todayMinus(days))` — `LocalDate` offset by `days`
- `#(randomInt(max))` or `#(randomInt(min,max))` — random integer in `[0, max)` or `[min, max)`
- `#(alphanumeric)`, `#(alphabetic)` (or `#(alpha)`), `#(numeric)`, `#(ascii)` — random string of default length `12`, or pass `(length)` / `(min,max)` (for example `#(alphanumeric(16))` or `#(numeric(4,8))`)

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
