# HTTP Fuzzers

CATS has currently 26 registered `HTTP` Fuzzers. The names below match
`cats list --fuzzers`:

- `BypassAuthentication` - remove authentication headers and check access control
- `CheckDeletedResourcesNotAvailable` - verify resources are unavailable after successful deletes
- `CustomHttpMethods` - send hypothetical methods not expected for REST APIs
- `DummyRequest` - send a dummy JSON request
- `EmptyBody` - send an empty-string body
- `EmptyJsonArrayBody` - send an empty JSON array body
- `EmptyJsonBody` - send an empty JSON object body
- `HappyPath` - send a request with all fields and headers populated
- `HttpMethods` - send undocumented HTTP methods
- `InsecureDirectObjectReferences` - replace ID fields with alternative values to detect IDOR
- `InsertRandomValuesInBody` - insert invalid data into a valid request body
- `MalformedJson` - send malformed JSON
- `NonRestHttpMethods` - send WebDAV methods not expected for REST APIs
- `NullBody` - send a null body
- `NullUnicodeBody` - send a `\\u0000` body
- `NullUnicodeSymbolBody` - send a Unicode null-symbol body
- `RandomDummyInvalidJsonBody` - send dummy invalid JSON
- `RandomNegativeDecimalBody` - send a random negative decimal body
- `RandomNegativeIntegerBody` - send a random negative integer body
- `RandomPositiveDecimalBody` - send a random positive decimal body
- `RandomPositiveIntegerBody` - send a random positive integer body
- `RandomResources` - send random resource identifiers in path variables
- `RandomStringBody` - send a random string body
- `RandomUnicodeBody` - send a random Unicode string body
- `ZeroDecimalBody` - send a decimal zero body
- `ZeroIntegerBody` - send an integer zero body

Run only these fuzzers with `--checkHttp`.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```
