# HTTP Fuzzers
CATS has currently 24 registered `HTTP` Fuzzers:
- `BypassAuthenticationFuzzer` - check if an authentication header is supplied; if yes try to make requests without it
- `CheckDeletedResourcesNotAvailableFuzzer` - checks that resources are not available through `GET` after a successful `DELETE`
- `DummyRequestFuzzer` - send a dummy json request {'cats': 'cats'}
- `EmptyBodyFuzzer` - send a request with a empty string body
- `EmptyJsonArrayBody` - send a request with a empty json array body
- `EmptyJsonBody` - send a request with a empty json body
- `HappyFuzzer` - send a request with all fields and headers populated
- `HttpMethodsFuzzer` - iterate through each undocumented HTTP method and send an empty request
- `InsertRandomValuesInBody` - insert invalid data within a valid request body
- `MalformedJsonFuzzer` - send a malformed json request which has the String 'bla' at the end
- `NonRestHttpMethodsFuzzer` - iterate through a list of HTTP method specific to the WebDav protocol that are not expected to be implemented by REST APIs
- `NullBodyFuzzer` - send a request with a NULL body
- `NullUnicodeBody` - send a request with a \u0000 body
- `NullUnicodeSymbolBody` - send a request with a ␀ body
- `RandomDummyInvalidJsonBody` - send a request with dummy invalid json body
- `RandomNegativeDecimalBodyFuzzer` - send a request with a random negative decimal body
- `RandomNegativeIntegerBodyFuzzer` - send a request with a random negative integer body
- `RandomPositiveDecimalBodyFuzzer` - send a request with a random positive decimal body
- `RandomPositiveIntegerBodyFuzzer` - send a request with a random positive integer body
- `RandomResourcesFuzzer` - iterate through each path variable and send random resource identifiers
- `RandomStringBodyFuzzer` - send a request with a random string body
- `RandomUnicodeBodyFuzzer` - send a request with a random unicode string body
- `ZeroDecimalBodyFuzzer` - send a request with decimal 0.0 as body
- `ZeroIntegerBodyFuzzer` - send a request with integer 0 (zero) as body

You can run only these Fuzzers by supplying the `--checkHttp` argument.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```