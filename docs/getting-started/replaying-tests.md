---
sidebar_position: 7
description: How to replay specific tests from a CATS run
---

# Replaying Tests

CATS will output both an HTML file that will be linked in the summary report and individual JSON files. The JSON files can be used to replay tests at later times.
When replaying a test (or a list of tests), by default, CATS won't produce any report. The output will be solely available in the console.
This is useful when you want to see the exact behaviour of the specific test or attach it in a bug report for example.

The syntax for replaying tests is the following:

```bash
cats replay "Test1,Test233,Test15.json,dir/Test19.json"
```

Important notes:
- test names are separated by comma `,`
- if you provide a `.json` extension to a test name, that file will be searched as a path i.e. it will search for `Test15.json` in the current folder and `Test19.json` in the `dir` folder
- if you don't provide a json extension to a test name, CATS searches for that test in the `cats-report` folder i.e. `cats-report/Test1.json` and `cats-report/Test233.json`

You can also use environment variables for headers, URLs, and request bodies (JSON and `application/x-www-form-urlencoded`) when replaying tests using `$$variable`. CATS resolves `$$variable` placeholders from the process environment first, then from `./.env` (or the file supplied via `--envFile`, unless disabled with `--noEnvFile`), applying proper JSON escaping or URL encoding automatically.

:::tip
When replaying tests you can override recorded headers with `-H` (`--header`) and recorded request body fields with `-R` (`--refDataParam`):

```bash
cats replay Test1 -H "Authorization=Bearer token" -R "password=mySecret" -R "auth.clientSecret=$$CLIENT_SECRET"
```
:::

## Printing equivalent `curl` commands

Pass `--curl` to print the equivalent `curl` command(s) for the selected test case(s) without executing any HTTP calls. Masked `$$VAR` placeholders in headers, URLs, and bodies are emitted as shell `"${VAR}"` expansions without requiring the variables to be exported first (explicit `-H` and `-R` overrides are still applied):

```bash
cats replay Test1 --curl
cats replay --errors --curl
```

For requests with a binary body, such as `multipart/form-data`, the test case JSON stores the exact bytes that were sent, Base64 encoded, in the request `encodedBody` field; the `payload` field holds only a printable summary (file name, size and SHA-256 of each file). Replaying a test sends the stored bytes unchanged, including the original boundary. In this case, `--curl` prints a command that decodes the stored body and sends it using `--data-binary @-`:

```bash
printf '%s' '<base64 body>' | base64 --decode | curl -i -X POST -H 'Content-Type: multipart/form-data; boundary=...' --data-binary @- 'http://localhost:8080/documents/doc-1/attachments'
```

## Stateful replay and exporting functional flows

When a test case depends on resource IDs created earlier in the run (via runtime resource correlation), replaying that single test later may fail with `404` if the resource no longer exists.

- Pass `--stateful` to automatically replay the upstream `2XX` producer test(s) from the report folder first, extract fresh resource IDs from the live responses, and substitute them into the replayed request:
  ```bash
  cats replay Test42 --stateful
  ```
- Pass `--export-flow=FILE` to export the selected test(s) and their upstream producer chain as a standalone, runnable `steps:` functional flow YAML file without executing HTTP calls:
  ```bash
  cats replay Test42 --export-flow reproducer-flow.yml
  cats run reproducer-flow.yml -c openapi.yml -s http://localhost:8080
  ```

## Reporting and service overrides

You can enable reporting by supplying an `--output` argument with the location folder that will be used to write the report files. Please note that CATS won't produce a full report, but rather
individual `TestXXX.json` and `TestXXX.html` files.

The `replay` sub-command also supports replacing the base URL of the service. This is useful when replaying the test(s) on a different environment than they originally ran against.
This is achieved using the `--server` argument. You can also adjust `--callTimeout` and `--maxResponseBytes` for replayed requests. 

:::note
Please note that you only need to provide the base URL of the service in the `--server` argument.
:::

# Replaying Errors

You can replay failed tests using the `--errors` argument. This will replay all failed tests that were flagged as `error` from the last run. 
You can supply the path to the reporting folder using the `--reportFolder` argument.

# Replaying Warnings

You can replay tests that were flagged as `warn` using the `--warnings` argument. This will replay all tests that were flagged as `warn` from the last run. 
You can supply the path to the reporting folder using the `--reportFolder` argument.


