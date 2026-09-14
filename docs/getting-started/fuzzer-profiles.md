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

Use a profile with the OpenAPI fuzzing command:

```bash
cats -c openapi.yml -s http://localhost:8080 --profile security
cats -c openapi.yml -s http://localhost:8080 --profile quick
```

The built-in profiles are:

- `health-check` runs `HappyPath` to verify that endpoints are reachable.
- `security` focuses on OWASP-style injection, authentication, IDOR, resource, and security-header checks.
- `quick` is a fast smoke test covering critical positive and negative scenarios.
- `compliance` focuses on API Security Top 10 and contract-related checks.
- `ci` provides a balanced suite for CI/CD pipelines.
- `full` runs all fuzzers and is the default.

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
