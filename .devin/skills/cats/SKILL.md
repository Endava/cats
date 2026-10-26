---
name: cats
description: "End-to-end REST API testing, contract analysis, functional workflow authoring, fuzzing, and bug triage using the CATS CLI. Invoke when the user asks to test an API end-to-end, audit an OpenAPI spec and fuzz its live server, or orchestrate a full CATS testing pipeline."
argument-hint: "[-c <openapi.yml>] [-s <server-url>]"
allowed-tools:
  - read
  - edit
  - grep
  - glob
  - exec
triggers:
  - user
  - model
---

# CATS — End-to-End REST API Testing Orchestrator

Use this orchestrator skill to run a complete, closed-loop API quality and security workflow with CATS. For focused tasks, delegate to or follow the specialized CATS skills:

1. **`/cats-analyze-api`** — Validate the OpenAPI contract, compute the 0–100 Fuzzability Score & Producer $\rightarrow$ Consumer dependency DAG, and run 31 contract linters (`cats validate`, `cats analyze`, `cats stats`, `cats lint`).
2. **`/cats-fuzz-api`** — Run a pre-flight smoke probe (`cats smoke`), configure auth/headers, and execute stateful negative, boundary, and security fuzzing (`cats`, `cats random`, `cats template`, `cats run security.yml`).
3. **`/cats-triage-results`** — Cluster `cats-report` failures into deduplicated root causes, auto-mine `refData.yml` and `correlations.yml`, replay reproducers, verify fixes live (`cats triage --verify`), and compare baselines (`cats compare`).
4. **`/cats-write-tests`** — Generate schema-valid payloads (`cats generate`) and write stateful multi-step functional flows (`cats run flows.yml`) and cross-field correlation rules (`correlations:`).

---

## Standard End-to-End Agent Workflow

### Phase 1: Discover Contract & Inspect Fuzzability (Offline)
1. Locate the OpenAPI/Swagger specification in the workspace (`*.yaml`, `*.yml`, `*.json`).
2. Validate and analyze the contract using JSON output (`-j`):
   ```bash
   cats validate -c <spec> -j
   cats analyze -c <spec> -j
   cats lint -c <spec> -j
   ```
3. Review `fuzzabilityScore`, `orphanPathParameters` (path variables without a `POST` producer), and `chains` (Producer $\rightarrow$ Consumer operations).

### Phase 2: Pre-Flight Smoke Probe Against the Live Server
Never launch a full fuzzing run before verifying reachability, authentication, and seed requirements:
```bash
cats smoke -c <spec> -s <server_url> -j
```
- **`UNREACHABLE_SERVER`**: Start the target service or fix `-s <server_url>`.
- **`AUTH_FAILURE_ALL_ENDPOINTS`**: Configure `-H "Authorization=Bearer ..."` or `--headers headers.yml` / `--authConfig auth.yml` and re-run `cats smoke`.
- **`NEEDS_SEED_OR_AUTH_CONFIG`**: Run `cats init -c <spec>` to scaffold `headers.yml` and `refData.yml` for unresolved path parameters.

### Phase 3: Stateful Fuzzing & Triage Loop
1. Run stateful fuzzing using the root `cats` command (`--srs` enables runtime resource correlation; `--srw` enables 4-wave execution):
   ```bash
   cats -c <spec> -s <server_url> --srs --srw --profile ci -o cats-report
   ```
2. Triage the report and auto-extract configuration fixes:
   ```bash
   cats triage -r cats-report -c <spec> -j \
     --export-refdata refData.yml \
     --export-correlations correlations.yml \
     --export-contract-patch contract-patch.yml \
     --export-reproducers reproducers.sh
   ```
3. For **`SERVER_CRASH_5XX`** clusters: inspect the representative `Test*.json` and `curlReproducer`, fix the backend bug, and verify the fix in seconds:
   ```bash
   cats triage -r cats-report --verify -s <server_url>
   ```
4. For **`UNRESOLVED_PATH_PARAM_404`** or **`CROSS_FIELD_CONSTRAINT_4XX`** clusters: re-run CATS with the exported `--refData refData.yml` and `--correlations correlations.yml`.

### Phase 4: Stateful Functional Flows
Author end-to-end business workflows in `flows.yml` (see `/cats-write-tests`) and execute them:
```bash
cats run -c <spec> -s <server_url> flows.yml --correlations correlations.yml
```
