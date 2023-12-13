# Header Fuzzers
CATS has currently 36 registered `Header`  Fuzzers:
- `AbugidasCharsInHeadersFuzzer` -  iterate through each header and send requests with abugidas chars in the targeted header
- `CRLFHeaders` - iterate through each header and send CR & LF characters in the targeted header
- `CheckSecurityHeadersFuzzer` - check all responses for good practices around Security related headers like: [{name=Cache-Control, value=no-store}, {name=X-XSS-Protection, value=1; mode=block}, {name=X-Content-Type-Options, value=nosniff}, {name=X-Frame-Options, value=DENY}]
- `DummyAcceptHeadersFuzzer` - send a request with a dummy Accept header and expect to get 406 code
- `DummyContentTypeHeadersFuzzer` - send a request with a dummy Content-Type header and expect to get 415 code
- `DummyContentLengthHeadersFuzzer` - send a request with a dummy Content-Length header and expect to get 400 code
- `DummyTransferEncodingHeadersFuzzer` - send a request with a dummy Transfer-Encoding header and expect to get a 400 or 501 code
- `DuplicateHeaderFuzzer` - send a 'happy' flow request and duplicate an existing header
- `EmptyStringValuesInHeadersFuzzer` - iterate through each header and send requests with empty String values in the targeted header
- `ExtraHeadersFuzzer` - send a 'happy' flow request and add an extra field inside the request called 'Cats-Fuzzy-Header'
- `InvalidContentLengthHeadersFuzzer` - send a request with an invalid Content-Length header and expect to get 400 code
- `LargeNumberRandomAlphanumericHeaders` - send a 'happy' flow request with 10 000 extra random alphanumeric headers
- `LargeNumberRandomHeaders` - send a 'happy' flow request with 10 000 extra random headers
- `LeadingControlCharsInHeadersFuzzer` - iterate through each header and prefix values with control chars
- `LeadingWhitespacesInHeadersFuzzer` - iterate through each header and prefix value with unicode separators
- `LeadingMultiCodePointEmojisInHeadersFuzzer` - iterate through each header and prefix values with multi code point emojis
- `LeadingSingleCodePointEmojisInHeadersFuzzer` - iterate through each header and prefix values with single code point emojis
- `LeadingSpacesInHeadersFuzzer` - iterate through each header and send requests with spaces prefixing the value in the targeted header
- `RemoveHeadersFuzzer` - iterate through each header and remove different combinations of them
- `OnlyControlCharsInHeadersFuzzer` - iterate through each header and replace value with control chars
- `OnlySpacesInHeadersFuzzer` - iterate through each header and replace value with spaces
- `OnlyMultiCodePointEmojisInHeaders` - iterate through each header and send values replaced by multi code point emojis in the targeted header
- `OnlySingleCodePointEmojisInHeaders` - iterate through each header and send values replaced by single code point emojis in the targeted header
- `OnlyWhitespacesInHeadersFuzzer` - iterate through each header and replace value with unicode separators
- `ResponseHeadersMatchContractHeaders` - send a request with all fields and headers populated and checks if the response headers match the ones defined in the contract
- `TrailingSpacesInHeadersFuzzer` - iterate through each header and send requests with trailing spaces in the targeted header
- `TrailingControlCharsInHeadersFuzzer` - iterate through each header and trail values with control chars
- `TrailingMultiCodePointEmojisHeaders` - iterate through each header and send values suffixed with multi code point emojis in the targeted header
- `TrailingSingleCodePointEmojisHeaders` - iterate through each header and send values suffixed with single code point emojis in the targeted header
- `TrailingWhitespacesInHeadersFuzzer` - iterate through each header and trail values with unicode separators
- `UnsupportedAcceptHeadersFuzzer` - send a request with an unsupported Accept header and expect to get 406 code
- `UnsupportedContentTypesHeadersFuzzer` - send a request with an unsupported Content-Type header and expect to get 415 code
- `UserDictionaryHeadersFuzzer` - iterates through each request headers and sends values from the user supplied dictionary
- `VeryLargeStringsInHeadersFuzzer` - iterate through each header and send large values in the targeted header
- `VeryLargeUnicodeStringsInHeadersFuzzer` - iterate through each header and send large unicode values in the targeted header
- `ZalgoTextInHeadersFuzzer` - iterate through each header and send requests with zalgo text in the targeted header

You can run only these Fuzzers by supplying the `--checkHeaders` argument.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```