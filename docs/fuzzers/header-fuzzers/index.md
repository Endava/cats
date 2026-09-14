# Header Fuzzers

CATS has currently 39 registered `Header` Fuzzers. The names below match
`cats list --fuzzers`:

- `AbugidasInHeaders` - send abugidas characters in header values
- `AcceptLanguageHeaders` - send a locale `Accept-Language` header
- `CRLFHeaders` - send CR and LF characters in header values
- `CheckSecurityHeaders` - check responses for recommended security headers
- `DummyAcceptHeaders` - send a dummy `Accept` header and expect `406`
- `DummyContentLengthHeaders` - send an invalid `Content-Length` and expect `400`
- `DummyContentTypeHeaders` - send a dummy `Content-Type` and expect `415`
- `DummyTransferEncodingHeaders` - send a dummy `Transfer-Encoding` and expect `400` or `501`
- `DuplicateHeaders` - duplicate an existing header
- `EmptyStringsInHeaders` - send empty header values
- `ExtraHeaders` - add an unexpected `Cats-Fuzzy-Header`
- `InvalidContentLengthHeaders` - send an invalid `Content-Length` and expect `400`
- `LargeNumberOfRandomAlphanumericHeaders` - send 10,000 random alphanumeric headers
- `LargeNumberOfRandomHeaders` - send 10,000 random headers
- `LeadingControlCharsInHeaders` - prefix header values with control characters
- `LeadingMultiCodePointEmojisInHeaders` - prefix header values with multi-code-point emojis
- `LeadingSingleCodePointEmojisInHeaders` - prefix header values with single-code-point emojis
- `LeadingSpacesInHeaders` - prefix header values with spaces
- `LeadingWhitespacesInHeaders` - prefix header values with Unicode separators
- `OnlyControlCharsInHeaders` - replace header values with control characters
- `OnlyMultiCodePointEmojisInHeaders` - replace header values with multi-code-point emojis
- `OnlySingleCodePointEmojisInHeaders` - replace header values with single-code-point emojis
- `OnlySpacesInHeaders` - replace header values with spaces
- `OnlyWhitespacesInHeaders` - replace header values with Unicode separators
- `RemoveHeaders` - remove different combinations of headers
- `ResponseHeadersMatchContractHeaders` - compare response headers with contract headers
- `TrailingControlCharsInHeaders` - suffix header values with control characters
- `TrailingMultiCodePointEmojisHeaders` - suffix header values with multi-code-point emojis
- `TrailingSingleCodePointEmojisHeaders` - suffix header values with single-code-point emojis
- `TrailingSpacesInHeaders` - suffix header values with spaces
- `TrailingWhitespacesInHeaders` - suffix header values with Unicode separators
- `UnsupportedAcceptHeaders` - send an unsupported `Accept` header and expect `406`
- `UnsupportedContentTypesHeaders` - send an unsupported `Content-Type` and expect `415`
- `UserDictionaryHeaders` - send values from the user dictionary
- `VeryLargeStringsInHeaders` - send large string values
- `VeryLargeUnicodeStringsInHeaders` - send large Unicode values
- `ZalgoTextInHeaders` - send Zalgo text
- `ZeroWidthCharsInNamesHeaders` - inject zero-width characters in header names
- `ZeroWidthCharsInValuesHeaders` - send zero-width characters in header values

Run only these fuzzers with `--checkHeaders`.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```
