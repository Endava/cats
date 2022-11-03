# Header Fuzzers
CATS has currently 29 registered `Header`  Fuzzers:
- `AbugidasCharsInHeadersFuzzer` -  iterate through each header and send requests with abugidas chars in the targeted header
- `CheckSecurityHeadersFuzzer` - check all responses for good practices around Security related headers like: [{name=Cache-Control, value=no-store}, {name=X-XSS-Protection, value=1; mode=block}, {name=X-Content-Type-Options, value=nosniff}, {name=X-Frame-Options, value=DENY}]
- `DummyAcceptHeadersFuzzer` - send a request with a dummy Accept header and expect to get 406 code
- `DummyContentTypeHeadersFuzzer` - send a request with a dummy Content-Type header and expect to get 415 code
- `DuplicateHeaderFuzzer` - send a 'happy' flow request and duplicate an existing header
- `EmptyStringValuesInHeadersFuzzer` - iterate through each header and send requests with empty String values in the targeted header
- `ExtraHeaderFuzzer` - send a 'happy' flow request and add an extra field inside the request called 'Cats-Fuzzy-Header'
- `LargeValuesInHeadersFuzzer` - iterate through each header and send requests with large values in the targeted header
- `LeadingControlCharsInHeadersFuzzer` - iterate through each header and prefix values with control chars
- `LeadingWhitespacesInHeadersFuzzer` - iterate through each header and prefix value with unicode separators
- `LeadingMultiCodePointEmojisInHeadersFuzzer` - iterate through each header and prefix values with multi code point emojis
- `LeadingSingleCodePointEmojisInHeadersFuzzer` - iterate through each header and prefix values with single code point emojis
- `LeadingSpacesInHeadersFuzzer` - iterate through each header and send requests with spaces prefixing the value in the targeted header
- `LeadingWhitespacesInHeadersFuzzer` - iterate through each header and prefix value with unicode separators
- `RemoveHeadersFuzzer` - iterate through each header and remove different combinations of them
- `OnlyControlCharsInHeadersFuzzer` - iterate through each header and replace value with control chars
- `OnlySpacesInHeadersFuzzer` - iterate through each header and replace value with spaces
- `OnlyWhitespacesInHeadersFuzzer` - iterate through each header and replace value with unicode separators
- `TrailingSpacesInHeadersFuzzer` - iterate through each header and send requests with trailing spaces in the targeted header \
- `TrailingControlCharsInHeadersFuzzer` - iterate through each header and trail values with control chars
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