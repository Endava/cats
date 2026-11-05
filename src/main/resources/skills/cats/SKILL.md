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

1. **`/cats-analyze-api`** — Validate the OpenAPI contract, compute the 0–100 Fuzzability Score & Producer $\rightarrow$ Consumer dependency chains, and run the contract linters (`cats validate`, `cats analyze`, `cats stats`, `cats lint`).
2. **`/cats-fuzz-api`** — Run a pre-flight smoke probe (`cats smoke`), configure auth/headers, and execute stateful negative, boundary, and security fuzzing (`cats`, `cats random`, `cats template`, `cats run security.yml`).
3. **`/cats-triage-results`** — Cluster `cats-report` failures into deduplicated root causes, walk the per-cluster decision tree, mine `refData.yml` / `correlations.yml`, replay reproducers, verify fixes live (`cats triage --verify`), and compare baselines (`cats compare`).
4. **`/cats-write-tests`** — Generate schema-valid payloads (`cats generate`), write stateful multi-step functional flows (`cats run flows.yml`), cross-field correlation rules (`correlations:`), and reference data (`refData.yml`).

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
3. Review `fuzzabilityScore`, `orphanPathParameters` (path variables without a `POST` producer), `producerConsumerChains`, and `recommendations`.

### Phase 2: Pre-Flight Smoke Probe Against the Live Server
Never launch a full fuzzing run before verifying reachability, authentication, and seed requirements:
```bash
cats smoke -c <spec> -s <server_url> -j
```
- **`UNREACHABLE_SERVER`** / **`NO_OPERATIONS_EXECUTED`**: Start the target service, fix `-s <server_url>`, or check path/method filters.
- **`AUTH_FAILURE_ALL_ENDPOINTS`**: Configure credentials and re-run `cats smoke`. `$$VAR` placeholders resolve only when they are the **entire** value, so put the full header value in the env var and single-quote it in the shell: `export API_AUTH='Bearer eyJ...'` then `-H 'Authorization=$$API_AUTH'` (or `--headers headers.yml`, `--wfcAuth auth.yml`, `--basicAuth user:pass`).
- **`NEEDS_SEED_OR_AUTH_CONFIG`**: Run `cats init -c <spec>` to scaffold `headers.yml` and `refData.yml` for unresolved path parameters.
- **`READY`** only means at least one `2XX` and no `5XX`; still inspect `notFound404` and `validation4xx`.

### Phase 3: Stateful Fuzzing & Triage Loop
1. Run fuzzing using the root `cats` command. Stateful resource reuse and breadth-first waves are **on by default**; add `--preseedCollectionGets` to harvest existing IDs first:
   ```bash
   cats -c <spec> -s <server_url> --profile ci --preseedCollectionGets -o cats-report
   ```
   **Never pass `--srs` / `--srw`** (`--skipReportingForSuccess` / `--skipReportingForWarning`) or `-b -k` before triage: they drop the `2XX` and warning tests that `triage --export-refdata`, `replay --stateful`, and `--include-warnings` depend on.
2. Triage the report and auto-extract configuration fixes:
   ```bash
   cats triage -r cats-report -c <spec> -j \
     --export-refdata refData.yml \
     --export-correlations correlations.yml \
     --export-contract-patch contract-patch.yml \
     --export-reproducers reproducers.sh
   ```
3. Walk the per-cluster decision tree in `/cats-triage-results` to choose between a backend fix, `--refData`, `--correlations`, a contract fix, or a CLI flag. Never pass exported files back unreviewed: synthesized correlation rules carry placeholder values (typed from the contract when `-c` is passed) and heuristic `when:` triggers, and mined refData pins disable runtime resource reuse for those parameters.
4. For **`SERVER_CRASH_5XX`** clusters: inspect the representative `Test*.json` and `curlReproducer`, fix the backend bug, and verify:
   ```bash
   export Authorization='Bearer eyJ...'   # reports mask headers as $$<HeaderNameAlphanumeric>
   cats triage -r cats-report --verify -s <server_url>
   ```
   `INCONCLUSIVE` means the replay proved nothing (missing masked variable, unreachable server, unrelated `401`/`403`, or stale-ID `404`); check `verificationNote` and confirm with `cats replay TestN --stateful`.
5. Re-run CATS with the reviewed `--refData refData.yml` and `--correlations correlations.yml`.

### Phase 4: Stateful Functional Flows
Author end-to-end business workflows in `flows.yml` (see `/cats-write-tests`) and execute them. `cats run` ignores `--correlations`; put flow-level cross-field rules in the flow's own `correlate:` block:
```bash
cats run -c <spec> -s <server_url> flows.yml
```
