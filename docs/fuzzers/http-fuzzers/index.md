# HTTP Fuzzers

CATS has currently 33 registered `HTTP` Fuzzers. The names below match
`cats list --fuzzers`:

- `BypassAuthentication` - remove authentication headers and check access control
- `CheckDeletedResourcesNotAvailable` - verify resources are unavailable after successful deletes
- `CheckReadsStillWork` - repeat GET requests that succeeded before fuzzing, after all fuzzers finished for a path, to detect fuzzed writes that break reads
- `ConditionalEtag` - test `If-None-Match` on GET and stale `If-Match` on PUT/PATCH with previously observed ETags
- `CreatedResourceLocation` - retrieve a newly created resource from a same-origin `Location` pointing to a documented GET
- `CrossParentResourceIsolation` - probe a known child resource under another observed parent, reporting only confirmed identity exposure as a warning
- `CustomHttpMethods` - send hypothetical methods not expected for REST APIs
- `DummyRequest` - send a dummy JSON request
- `EmptyBody` - send an empty-string body
- `EmptyJsonArrayBody` - send an empty JSON array body
- `EmptyJsonBody` - send an empty JSON object body
- `HappyPath` - send a request with all fields and headers populated
- `HttpMethods` - send undocumented HTTP methods
- `InsecureDirectObjectReferences` - detect heuristic IDOR signals by replacing ID fields and verifying alternative IDs in successful responses
- `InsertRandomValuesInBody` - insert invalid data into a valid request body
- `MalformedJson` - send malformed JSON
- `NonRestHttpMethods` - send WebDAV methods not expected for REST APIs
- `NullBody` - send a null body
- `NullUnicodeBody` - send a `\\u0000` body
- `NullUnicodeSymbolBody` - send a Unicode null-symbol body
- `PutIdempotency` - repeat a successful PUT twice on a resource created in the current run and compare resulting GET state
- `RandomDummyInvalidJsonBody` - send dummy invalid JSON
- `RandomNegativeDecimalBody` - send a random negative decimal body
- `RandomNegativeIntegerBody` - send a random negative integer body
- `RandomPositiveDecimalBody` - send a random positive decimal body
- `RandomPositiveIntegerBody` - send a random positive integer body
- `RandomResources` - send random resource identifiers in path variables
- `RandomStringBody` - send a random string body
- `RandomUnicodeBody` - send a random Unicode string body
- `SensitiveDataExposure` - send a happy flow request and check that the response does not expose `writeOnly` fields or undeclared sensitive fields like passwords, secrets, or API keys
- `UpdateReadConsistency` - read back a created resource after a successful update and warn on differences in comparable writable fields
- `ZeroDecimalBody` - send a decimal zero body
- `ZeroIntegerBody` - send an integer zero body

Run only these fuzzers with `--checkHttp`.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```
