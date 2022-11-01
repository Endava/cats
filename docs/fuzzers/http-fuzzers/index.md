# HTTP Fuzzers
CATS has currently 6 registered `HTTP` Fuzzers:
- `BypassAuthenticationFuzzer` - check if an authentication header is supplied; if yes try to make requests without it
- `DummyRequestFuzzer` - send a dummy json request {'cats': 'cats'}
- `HappyFuzzer` - send a request with all fields and headers populated
- `HttpMethodsFuzzer` - iterate through each undocumented HTTP method and send an empty request
- `MalformedJsonFuzzer` - send a malformed json request which has the String 'bla' at the end
- `NonRestHttpMethodsFuzzer` - iterate through a list of HTTP method specific to the WebDav protocol that are not expected to be implemented by REST APIs

You can run only these Fuzzers by supplying the `--checkHttp` argument.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```