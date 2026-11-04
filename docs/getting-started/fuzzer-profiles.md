---
sidebar_position: 19
description: Select a predefined or custom set of CATS fuzzers
---

# Fuzzer Profiles

Profiles select a useful set of fuzzers without requiring a long
`--fuzzers` value. List the built-in profiles with:

```bash
cats list --profiles
```

You can also inspect a specific profile and see the description of each fuzzer it includes with:

```bash
cats explain --type profile security
```

Use a profile with the OpenAPI fuzzing command:

```bash
cats -c openapi.yml -s http://localhost:8080 --profile security
cats -c openapi.yml -s http://localhost:8080 --profile quick
cats -c openapi.yml -s http://localhost:8080 --profile coverage
cats -c openapi.yml -s http://localhost:8080 --profile crash
```

The built-in profiles are:

- `health-check` runs `HappyPath` to verify that endpoints are reachable.
- `coverage` runs a fast positive and combinatorial field-removal blitz (`HappyPath`, `OnlyRequiredFields`, `DummyContentTypeHeaders`, `EmptyJsonBody`, `DefaultValuesInFields`, `ExamplesFields`, `AlternativeDateTimeFormatsFields`, `QueryObjectAsRequestBody`, `RemoveFields`, `NumericValuesInStringFields`) to maximize operation and code coverage.
- `crash` runs high-impact boundary, OpenAPI constraint (`exclusiveMinimum`/`exclusiveMaximum`, `multipleOf`, `minItems`, `minProperties`, `oneOf`, `const`/`not`, simultaneous max boundaries), type-coercion, injection, mass-assignment (`readOnly`), HTTP parameter pollution, verb-override tunneling, control-character, Unicode, and resource fuzzers targeting `5XX` server crashes.
- `type-coercion` runs type-coercion fuzzers (along with `HappyPath` and `OnlyRequiredFields`) targeting automatic type conversion issues in frameworks and libraries.
- `security` focuses on OWASP-style injection (SQLi, NoSQLi, LDAP, XXE, SSTI, Command, Path Traversal, XSS, SSRF, ORM sort-column leak, HTTP Parameter Pollution), authentication, IDOR, mass assignment (including `readOnly` and `additionalProperties`), verb-override tunneling, idempotency conflicts, sensitive data exposure, resource lifecycle, and security-header checks.
- `quick` is a fast smoke test covering critical positive and negative scenarios.
- `compliance` focuses on OWASP API Security Top 10 (2023) and contract-related checks.
- `ci` provides a balanced suite for CI/CD pipelines covering functional correctness, boundaries, OpenAPI constraints, business logic, and security essentials.
- `full` runs all fuzzers and is the default.

The active fuzzer profile is also displayed in the generated HTML summary report (`cats-report/index.html`).

Use `--healthCheck` to run the minimal `health-check` profile before the full
fuzzing run:

```bash
cats -c openapi.yml -s http://localhost:8080 --healthCheck
```

You can provide additional profiles in a YAML file with `--profileFile`. The
format is:

```yaml
profiles:
  api-smoke:
    description: Smoke tests for the public API
    fuzzers:
      - HappyPath
      - OnlyRequiredFields
      - InvalidValuesInEnumsFields
```

If `--fuzzers` is supplied explicitly, it takes precedence over `--profile`.
The `--healthCheck` option selects `health-check` regardless of the profile
option.
