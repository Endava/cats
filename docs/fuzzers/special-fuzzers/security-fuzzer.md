---
sidebar_position: 2
description: Fuzz specific fields, field types, headers, or bodies with custom payloads using steps and fuzz blocks
---

# Security Fuzzer

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `SecurityFuzzer` |
| **Log Key** | N/A; step names and descriptions from the flow file identify executions. |
| **Description** | Injects custom security payloads (inline lists or external dictionary files) into explicitly targeted fields, field types, HTTP headers, or request bodies using the sequential `steps:` + `fuzz:` syntax. |
| **Enabled by default?** | No. Enable it with `cats run -c <contract> -s <server> <security-flow.yml>`. |
| **Target fields** | `fuzz.fields`, `fuzz.fieldTypes` (`string`, `email`, `http_headers`/`headers`, `http_body`/`body`), either one field at a time (`mode: single`) or simultaneously across all matching fields (`mode: cross`). |
| **Expected result** | The `status` and `expect` assertions configured on each step (typically `4XX` or a sanitized `2XX`, with `bodyNotContains` checks against error/reflection leaks). |
| **Fuzzing logic** | For each step with a `fuzz:` block, injects the configured `payloads` / `payloadsFile` into the selected targets while preserving schema-valid generated values (and any `body`/`correlate` overrides) for other fields, and can chain with follow-up steps in the same flow. |
| **Conditions when this fuzzer will be skipped** | When no security flow file is supplied, a path has no matching step, or required `fuzz` / `status` entries are missing. |
| **HTTP methods that will be skipped** | Methods not targeted by the step or unavailable on the selected OpenAPI path. |
| **Reporting** | Reports results against each step's configured `status` and `expect` assertions. |

You can use CATS with your own inline payloads or custom wordlists of [nasty strings](https://github.com/minimaxir/big-list-of-naughty-strings) via `cats run`:

```shell
cats run -c openapi.yml -s http://localhost:8080 securityFuzzer.yml
```

---

## Sequential `steps:` + `fuzz:` Syntax

`SecurityFuzzer` uses the same sequential `steps:` flow structure as [`FunctionalFlowFuzzer`](./functional-fuzzer), adding a `fuzz:` block to any step that injects custom security payloads:

```yaml
steps:
  - name: "petXssDictionaryCheck"
    description: "Inject XSS payloads from xss.txt into id and name fields on POST /pet"
    POST: "/pet"
    body:
      category: "dogs"
    fuzz:
      fields:
        - id
        - name
      payloadsFile: "files/xss.txt"
      mode: single
    expect:
      status: [200, 400, 422]
      bodyNotContains:
        - "<script>"
        - "SQLException"

  - name: "petCrossFieldInjectionCheck"
    description: "Inject SQLi and script payloads simultaneously across string and email fields"
    POST: "/pet"
    fuzz:
      fieldTypes:
        - string
        - email
      payloads:
        - "' OR '1'='1"
        - "<img src=x onerror=alert(1)>"
      mode: cross
    expect:
      status: [400, 422]
      bodyNotContains:
        - "Exception"
```

### `fuzz:` Block Reference

- **`fields`**: List of specific field paths (using dot-notation `pet.description` or `#`-notation `pet#description`) to fuzz.
- **`fieldTypes`**: List of OpenAPI data types or formats (`string`, `integer`, `number`, `email`, `uuid`, `date-time`, etc.), or special request targets:
  - `http_headers` (or `headers`): Fuzzes all HTTP request headers.
  - `http_body` (or `body`): Replaces the entire HTTP request body with each payload.
- **`payloads`**: Inline YAML list of payload strings to inject.
- **`payloadsFile`**: Path (or list of paths) to external wordlist files (one payload per line, ignoring blank lines and `#` comments). Can be combined with inline `payloads`.
- **`mode`**:
  - `single` (default): Sends one request per target field and per payload, keeping all other fields valid.
  - `cross` (aliases `all`, `simultaneous`, `combined`, `multi`): Injects each payload into **all** matching target fields simultaneously in a single request.

### Targeting All Paths, All HTTP Methods, and Multi-Step Second-Order Checks

- **All paths / all methods**: Use `ALL: "/pet"` to target all HTTP methods on `/pet`, `POST: "all"` to target `POST` across all paths, or `ALL: "all"` to target every method on every path. You can also pass a list of paths under `paths: ["/pet", "/users"]` with `method: POST` (or `method: ALL`).
- **Multi-step second-order checks**: Because `SecurityFuzzer` shares the `steps:` engine with `FunctionalFlowFuzzer`, you can combine a `POST` step containing `fuzz:` and `save:` with a follow-up `GET` step in the same file to detect stored XSS or second-order SQL injection.

---

## Migrating from the Legacy `SecurityFuzzer` Syntax (Deprecated)

:::warning Deprecation Notice
The legacy path-grouped `SecurityFuzzer` YAML syntax (top-level `/path:` or `all:` keys with `targetFields`, `targetFieldTypes`, `stringsFile`, `expectedResponseCode`, and `httpMethod`) is **deprecated** and **will be removed in CATS `16.x.x`**. Please migrate existing security fuzzer files to the `steps:` + `fuzz:` format.
:::

### Old path-grouped format (deprecated — to be removed in `16.x.x`)

```yaml
/pet:
  test_1:
    description: Run XSS scenarios on specific fields
    name: "My Pet"
    expectedResponseCode: 200
    httpMethod: POST
    targetFields:
      - pet#id
      - pet#description
    stringsFile: xss.txt

all:
  test_2:
    description: Run XSS scenarios on all string fields and headers
    expectedResponseCode: 400
    httpMethod: all
    targetFieldTypes:
      - string
      - http_headers
    stringsFile: xss.txt
```

### Equivalent new `steps:` + `fuzz:` format

```yaml
steps:
  - name: "xssSpecificFields"
    description: "Run XSS scenarios on specific fields"
    POST: "/pet"
    body:
      name: "My Pet"
    fuzz:
      fields:
        - pet.id
        - pet.description
      payloadsFile: xss.txt
      mode: single
    expect:
      status: 200

  - name: "xssAllStringFieldsAndHeaders"
    description: "Run XSS scenarios on all string fields and headers"
    ALL: "all"
    fuzz:
      fieldTypes:
        - string
        - http_headers
      payloadsFile: xss.txt
    expect:
      status: 400
```
