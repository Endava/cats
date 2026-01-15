# Field Fuzzers

CATS has currently 83 registered `Field` Fuzzers:

- `AbugidasInStringFields` - iterate through each field and send values containing abugidas chars
- `AdditionalPropertiesMassAssignmentFields` - iterate through each object without additional properties field and replace it with object containing unexpected extra properties values
- `BidirectionalOverrideFields` - iterate through each string field and replace it with bidirectional-override characters values
- `CommandInjectionInStringFields` - iterate through each string field and send OS command injection payloads to detect command injection vulnerabilities
- `DateRangeInversion` - send requests where the start date field (e.g., startDate, checkIn) is set after the end date field (e.g., endDate, checkOut) to test temporal validation
- `DecimalFieldsLeftBoundaryFuzzer` - iterate through each Number field (either float or double) and send requests with outside the range values on the left side in the targeted field
- `DecimalFieldsRightBoundaryFuzzer` - iterate through each Number field (either float or double) and send requests with outside the range values on the right side in the targeted field
- `DecimalValuesInIntegerFieldsFuzzer` - iterate through each Integer field and send requests with decimal values in the targeted field
- `DecimalNumbersInIntegerFields` - iterate through each Integer field and send decimal values
- `DefaultValuesInFieldsFuzzer` - iterate through each field with default values defined and send a happy flow request
- `DuplicateKeysFields` - duplicates each JSON key to detect first-wins/last-wins parsing ambiguities per RFC 8259
- `EmptyStringValuesInFieldsFuzzer` - iterate through each field and send requests with empty String values in the targeted field
- `EnumCaseVariantFields` - iterate through each enum field and send case-variant values to test case sensitivity of enum handling
- `ExamplesFields` - send a request for every unique example
- `ExtremeNegativeValueDecimalFieldsFuzzer` - iterate through each Number field and send requests with the lowest value possible in the targeted field
- `ExtremeNegativeValueIntegerFieldsFuzzer` - iterate through each Integer field and send requests with the lowest value possible in the targeted field
- `ExtremePositiveValueDecimalFieldsFuzzer` - iterate through each Number field and send requests with the highest value possible in the targeted field
- `ExtremePositiveValueInIntegerFieldsFuzzer` - iterate through each Integer field and send requests with the highest value possible in the targeted field
- `FullwidthBracketsFields` - insert fullwidth '<' and '>' to test for markup filter bypass
- `HangulFillerFields` - inject Hangul filler characters to test for hidden-input handling
- `HomoglyphEnumFields` - iterate through each enum value field and replace it with homoglyph-altered value values
- `InsertWhitespacesInFieldNamesField` - iterates through each request field name and insert random whitespaces
- `IntegerFieldsLeftBoundaryFuzzer` - iterate through each Integer field and send requests with outside the range values on the left side in the targeted field
- `IntegerFieldsRightBoundaryFuzzer` - iterate through each Integer field and send requests with outside the range values on the right side in the targeted field
- `InvalidValuesInEnumsFieldsFuzzer` - iterate through each ENUM field and send invalid values
- `InvalidReferencesFieldsFuzzer` - iterate through each path fuzz the path parameters with invalid references
- `IterateThroughEnumValuesFieldsFuzzer` - iterate through each enum field and send happy flow requests iterating through each possible enum values
- `LeadingWhitespacesInFieldsTrimValidateFuzzer` - iterate through each field and send requests with Unicode whitespaces and invisible separators prefixing the current value in the targeted field
- `LeadingControlCharsInFieldsTrimValidateFuzzer` - iterate through each field and send requests with Unicode control chars prefixing the current value in the targeted field
- `LeadingSingleCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send values prefixed with single code points emojis
- `LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send values prefixed with multi code points emojis
- `LowercaseExpandingBytesInStringFields` -  iterate to string fields and send values that expand the byte representation when lowercased
- `LowercaseExpandingLengthInStringFields` - iterate to string fields and send values that expand their length when lowercased
- `MassAssignment` - adds undeclared fields to the request payload to detect Mass Assignment vulnerabilities where APIs blindly bind user input
- `MaxLengthExactValuesInStringFieldsFuzzer` - iterate through each string fields that have maxLength declared and send requests with values matching the maxLength size/value in the targeted field
- `MaximumExactValuesInNumericFieldsFuzzer` - iterate through each integer fields that have maximum declared and send requests with values matching the maximum size/value in the targeted field
- `MinGreaterThanMaxFields` - sends a request where the lower-bound field (e.g., minAmount) is set greater than the upper-bound field (e.g., maxAmount)
- `MinLengthExactValuesInStringFieldsFuzzer` - iterate through each string fields that have minLength declared and send requests with values matching the minLength size/value in the targeted field
- `MinimumExactValuesInNumericFieldsFuzzer` - iterate through each number fields that have minimum declared and send requests with values matching the minimum size/value in the targeted field
- `NewFieldsFuzzer` - send a 'happy' flow request and add a new field inside the request called 'catsFuzzyField'
- `NoSqlInjectionInStringFields` - iterate through each string field and send NoSQL injection payloads to detect NoSQL injection vulnerabilities
- `NullValuesInFieldsFuzzer` - iterate through each field and send requests with null values in the targeted field
- `OnlyControlCharsInFieldsTrimValidateFuzzer` - iterate through each field and send  values with control chars only
- `OnlyWhitespacesInFieldsTrimValidateFuzzer` - iterate through each field and send  values with unicode separators only
- `OnlySingleCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send  values with single code point emojis only
- `OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send  values with multi code point emojis only
- `OverflowArraySizeFields` - iterate through each array field and replace it with overflow array values
- `OverflowMapSizeFields` - iterate through each dictionary/hashmap field and replace it with overflow dictionary/hashmap values
- `PrefixNumbersWithZeroFields` - iterate through each numeric field and send values as strings with leading zeros to test type validation
- `RandomStringsInBooleanFields` - iterate through each Boolean field and send random strings
- `RemoveFieldsFuzzer` - iterate through each request fields and remove certain fields according to the supplied 'fieldsFuzzingStrategy'
- `ReplaceArraysWithPrimitivesFields` - iterate through each array field and replace it with primitive values
- `ReplaceArraysWithSimpleObjectsFields` - iterate through each array field and replace it with simple object values
- `ReplaceObjectsWithArraysFields` - iterate through each object field and replace it with array values
- `ReplaceObjectsWithPrimitivesFields` - iterate through each non-primitive field and replace it with primitive values
- `ReplacePrimitivesWithArraysFields` - iterate through each primitive field and replace it with array values
- `ReplacePrimitivesWithObjectsFields` - iterate through each primitive field and replace it with object values
- `SSRFInUrlFields` - iterate through URL-type fields and send SSRF payloads to detect Server-Side Request Forgery vulnerabilities
- `SqlInjectionInStringFields` - iterate through each string field and send SQL injection payloads to detect SQL injection vulnerabilities
- `StringFieldsLeftBoundaryFuzzer` - iterate through each String field and send requests with outside the range values on the left side in the targeted field
- `StringFieldsRightBoundaryFuzzer` - iterate through each String field and send requests with outside the range values on the right side in the targeted field
- `StringFormatAlmostValidValuesFuzzer` - iterate through each String field and get its 'format' value (i.e. email, ip, uuid, date, datetime, etc); send requests with values which are almost valid (i.e. email@yhoo. for email, 888.1.1. for ip, etc)  in the targeted field
- `StringFormatTotallyWrongValuesFuzzer` - iterate through each String field and get its 'format' value (i.e. email, ip, uuid, date, datetime, etc); send requests with values which are totally wrong (i.e. abcd for email, 1244. for ip, etc)  in the targeted field
- `StringsInNumericFieldsFuzzer` - iterate through each Integer (int, long) and Number field (float, double) and send requests having the `fuzz` string value in the targeted field
- `SwapDiscriminatorValuesFields` - iterate through each discriminator field and replace it with swapped values values
- `TemporalLogicFields` - sends semantically invalid date values (e.g., startDate after endDate, expired tokens, future birth dates) to verify if the backend enforces logical date constraints
- `TrailingWhitespacesInFieldsTrimValidateFuzzer` - iterate through each field and send requests with trailing with Unicode whitespaces and invisible separators in the targeted field
- `TrailingControlCharsInFieldsTrimValidateFuzzer` - iterate through each field and send requests with trailing with Unicode control chars in the targeted field
- `TrailingSingleCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send values trailed with single code point emojis
- `TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send values trailed with multi code point emojis
- `UppercaseExpandingBytesInStringFields` - iterate to string fields and send values that expand the byte representation when uppercased
- `UppercaseExpandingLengthInStringFields` - iterate to string fields and send values that expand their length when uppercased
- `UserDictionaryFieldsFuzzer` - iterates through each request fields and sends values from the user supplied dictionary
- `VeryLargeStringsFuzzer` - iterate through each String field and send requests with very large values (40000 characters) in the targeted field
- `VeryLargeDecimalsInNumericFieldsFuzzer` - iterate through each numeric field and send requests with very large numbers (40000 characters) in the targeted field
- `VeryLargeIntegersInNumericFieldsFuzzer` - iterate through each numeric field and send requests with very large numbers (40000 characters) in the targeted field
- `VeryLargeUnicodeStringsInFieldsFuzzer` - iterate through each field and send requests with very large random unicode values in the targeted field
- `WithinControlCharsInFieldsSanitizeValidateFuzzer` - iterate through each field and send values containing unicode control chars
- `WithinSingleCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send values containing single code point emojis
- `WithinMultiCodePointEmojisInFieldsTrimValidateFuzzer` - iterate through each field and send values containing multi code point emojis
- `XssInjectionInStringFields` - iterate through each string field and send XSS payloads to detect Cross-Site Scripting vulnerabilities
- `ZalgoTextInStringFieldsValidateSanitizeFuzzer` - iterate through each field and send values containing zalgo text
- `ZeroWidthCharsInNamesFields` - iterate through each field and insert zero-width characters in the field names
- `ZeroWidthCharsInValuesFields` - iterate through each field and send values containing zero-width characters

You can run only these Fuzzers by supplying the `--checkFields` argument.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```