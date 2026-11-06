# Field Fuzzers

CATS has currently 125 registered `Field` Fuzzers. The names below match the
current output of `cats list --fuzzers`.

- `AbugidasInStringFields` - iterate through each field and send values containing abugidas chars
- `AdditionalPropertiesMassAssignmentFields` - iterate through each object without additional properties field and replace it with object containing unexpected extra properties values
- `AllFieldsAtMaxBoundary` - sets all boundary-constrained fields simultaneously to their maximum valid OpenAPI boundary in a single request
- `ArithmeticTotalMismatchFields` - sends a total field that contradicts its sibling component fields (e.g., unitPrice * quantity != totalAmount) to verify server-side total calculation
- `BidirectionalOverrideFields` - iterate through each string field and replace it with bidirectional-override characters values
- `CommandInjectionInStringFields` - iterate through each string field and send OS command injection payloads to detect command injection vulnerabilities
- `ConfirmationFieldsMismatch` - sends mismatched values in sibling confirmation fields (e.g., password and confirmPassword, email and confirmEmail) to verify cross-field equality validation
- `ConstAndNotSchemaFields` - iterate through each field with const or not schema constraint and send value violating const or matching not schema values
- `ControlCharsInStringFields` - iterate through each string field and send values containing NUL bytes and control characters
- `CursorAndPaginationTokenFields` - iterates through cursor and pagination token fields and sends Base64 payloads with unexpected inner structures to verify cursor decoder robustness
- `DateRangeInversion` - send requests where the start date field (e.g., startDate, checkIn) is set after the end date field (e.g., endDate, checkOut) to test temporal validation
- `DecimalFieldsLeftBoundary` - iterate through each Number field (either float or double) and send requests with outside the range values on the left side in the targeted field
- `DecimalFieldsRightBoundary` - iterate through each Number field (either float or double) and send requests with outside the range values on the right side in the targeted field
- `DecimalNumbersInIntegerFields` - iterate through each Integer field and send decimal values
- `DefaultValuesInFields` - iterate through each field with default values defined and send a happy flow request
- `DuplicateKeysFields` - duplicates each JSON key to detect first-wins/last-wins parsing ambiguities per RFC 8259
- `EmojisInStringFields` - iterate through each field and send values containing 4-byte supplementary plane emojis
- `EmptyStringsInFields` - iterate through each field and send requests with empty String values in the targeted field
- `EnumCaseVariantFields` - iterate through each enum field and send case-variant values to test case sensitivity of enum handling
- `ExamplesFields` - send a request for every unique example
- `ExclusiveBoundaryNumbersInNumericFields` - iterate through each numeric with exclusiveMinimum/exclusiveMaximum field and send exact excluded boundary values
- `ExtremeNegativeNumbersInDecimalFields` - iterate through each Number field and send requests with the lowest value possible in the targeted field
- `ExtremeNegativeNumbersInIntegerFields` - iterate through each Integer field and send requests with the lowest value possible in the targeted field
- `ExtremePositiveNumbersInDecimalFields` - iterate through each Number field and send requests with the highest value possible in the targeted field
- `ExtremePositiveNumbersInIntegerFields` - iterate through each Integer field and send requests with the highest value possible in the targeted field
- `FullwidthBracketsFields` - insert fullwidth '<' and '>' to test for markup filter bypass
- `HangulFillerFields` - inject Hangul filler characters to test for hidden-input handling
- `HomoglyphEnumFields` - iterate through each enum value field and replace it with homoglyph-altered value values
- `HttpParameterPollutionFields` - duplicates query parameters with conflicting values in the URL to detect HTTP Parameter Pollution
- `InsertWhitespacesInFieldNamesField` - iterates through each request field name and insert random whitespaces
- `IntegerFieldsLeftBoundary` - iterate through each Integer field and send requests with outside the range values on the left side in the targeted field
- `IntegerFieldsRightBoundary` - iterate through each Integer field and send requests with outside the range values on the right side in the targeted field
- `InvalidValuesInEnumsFields` - iterate through each ENUM field and send invalid values
- `InvalidReferencesFields` - iterate through each path fuzz the path parameters with invalid references
- `IterateThroughEnumValuesFields` - iterate through each enum field and send happy flow requests iterating through each possible enum values
- `LeadingWhitespacesInFields` - iterate through each field and send requests with Unicode whitespaces and invisible separators prefixing the current value in the targeted field
- `LeadingControlCharsInFields` - iterate through each field and send requests with Unicode control chars prefixing the current value in the targeted field
- `LeadingSingleCodePointEmojisInFields` - iterate through each field and send values prefixed with single code points emojis
- `LeadingMultiCodePointEmojisInFields` - iterate through each field and send values prefixed with multi code points emojis
- `LowercaseExpandingBytesInStringFields` -  iterate to string fields and send values that expand the byte representation when lowercased
- `LowercaseExpandingLengthInStringFields` - iterate to string fields and send values that expand their length when lowercased
- `MassAssignment` - adds undeclared fields to the request payload to detect Mass Assignment vulnerabilities where APIs blindly bind user input
- `MaxLengthExactValuesInStringFields` - iterate through each string fields that have maxLength declared and send requests with values matching the maxLength size/value in the targeted field
- `MaximumExactNumbersInNumericFields` - iterate through each integer fields that have maximum declared and send requests with values matching the maximum size/value in the targeted field
- `MinGreaterThanMaxFields` - sends a request where the lower-bound field (e.g., minAmount) is set greater than the upper-bound field (e.g., maxAmount)
- `MinLengthExactValuesInStringFields` - iterate through each string fields that have minLength declared and send requests with values matching the minLength size/value in the targeted field
- `MinimumExactNumbersInNumericFields` - iterate through each number fields that have minimum declared and send requests with values matching the minimum size/value in the targeted field
- `MutuallyExclusiveFields` - populates mutually exclusive sibling fields (e.g., discountAmount and discountPercentage, creditAmount and debitAmount) simultaneously to verify mutual-exclusion validation
- `NewFields` - send a 'happy' flow request and add a new field inside the request called 'catsFuzzyField'
- `NoSqlInjectionInStringFields` - iterate through each string field and send NoSQL injection payloads to detect NoSQL injection vulnerabilities
- `NonMultipleOfNumbersInNumericFields` - iterate through each numeric with multipleOf constraint field and send non-multiple numeric values
- `NullValuesInFields` - iterate through each field and send requests with null values in the targeted field
- `OneOfMultipleBranchesMergedFields` - merges properties from two distinct oneOf schema branches into a single payload to verify exclusive oneOf validation
- `OnlyControlCharsInFields` - iterate through each field and send values with control chars only
- `OnlyWhitespacesInFields` - iterate through each field and send values with unicode separators only
- `OnlySingleCodePointEmojisInFields` - iterate through each field and send values with single code point emojis only
- `OnlyMultiCodePointEmojisInFields` - iterate through each field and send values with multi code point emojis only
- `OverflowArraySizeFields` - iterate through each array field and replace it with overflow array values
- `OverflowMapSizeFields` - iterate through each dictionary/hashmap field and replace it with overflow dictionary/hashmap values
- `PathBodyIdMismatch` - sends a body identifier that contradicts the URL path parameter on write requests to detect split-brain updates and BOLA
- `PathTraversalInStringFields` - iterate through each string field and send path traversal payloads to detect local file inclusion or filesystem path leaks
- `RandomStringsInBooleanFields` - iterate through each Boolean field and send random strings
- `ReadOnlyFieldsMassAssignment` - sends schema-declared readOnly fields in write requests to detect readOnly property mass assignment
- `RemoveFields` - iterate through each request fields and remove certain fields according to the supplied 'fieldsFuzzingStrategy'
- `ReplaceArraysWithPrimitivesFields` - iterate through each array field and replace it with primitive values
- `ReplaceArraysWithSimpleObjectsFields` - iterate through each array field and replace it with simple object values
- `ReplaceObjectsWithArraysFields` - iterate through each object field and replace it with array values
- `ReplaceObjectsWithPrimitivesFields` - iterate through each non-primitive field and replace it with primitive values
- `ReplacePrimitivesWithArraysFields` - iterate through each primitive field and replace it with array values
- `ReplacePrimitivesWithObjectsFields` - iterate through each primitive field and replace it with object values
- `SSRFInUrlFields` - iterate through URL-type fields and send SSRF payloads to detect Server-Side Request Forgery vulnerabilities
- `SearchAndFilterExpressionFields` - iterates through search, query, and filter fields and sends malformed regex, Lucene, and wildcard escape expressions to verify parser error handling
- `SortColumnOrmLeakFields` - iterates through sort/order-by fields and sends invalid or nested ORM property names to verify sort column validation
- `SqlInjectionInStringFields` - iterate through each string field and send SQL injection payloads to detect SQL injection vulnerabilities
- `StringFieldsLeftBoundary` - iterate through each String field and send requests with outside the range values on the left side in the targeted field
- `StringFieldsRightBoundary` - iterate through each String field and send requests with outside the range values on the right side in the targeted field
- `StringFormatAlmostValidValues` - iterate through each String field and send almost-valid format values
- `StringFormatTotallyWrongValues` - iterate through each String field and send totally invalid format values
- `StringsInNumericFields` - iterate through each Integer and Number field and send requests having the `fuzz` string value in the targeted field
- `SwapDiscriminatorValuesFields` - iterate through each discriminator field and replace it with swapped values values
- `TemporalLogicFields` - sends semantically invalid date values (e.g., startDate after endDate, expired tokens, future birth dates) to verify if the backend enforces logical date constraints
- `TimezoneAndLocaleCodeFields` - iterate through each timezone, locale, currency, and country code field and send invalid IANA, BCP-47, and ISO code values
- `TrailingWhitespacesInFields` - iterate through each field and send values trailed with Unicode whitespaces
- `TrailingControlCharsInFields` - iterate through each field and send values trailed with Unicode control chars
- `TrailingSingleCodePointEmojisInFields` - iterate through each field and send values trailed with single code point emojis
- `TrailingMultiCodePointEmojisInFields` - iterate through each field and send values trailed with multi code point emojis
- `UnderflowArraySizeFields` - iterate through each array with minItems constraint field and send underflow array values
- `UnderflowMapSizeFields` - iterate through each object/map with minProperties constraint field and send underflow object/map values
- `UppercaseExpandingBytesInStringFields` - iterate to string fields and send values that expand the byte representation when uppercased
- `UppercaseExpandingLengthInStringFields` - iterate to string fields and send values that expand their length when uppercased
- `UserDictionaryFields` - iterates through each request fields and sends values from the user supplied dictionary
- `VeryLargeStringsInFields` - iterate through each String field and send requests with very large values (40000 characters) in the targeted field
- `VeryLargeDecimalsInNumericFields` - iterate through each numeric field and send requests with very large numbers (40000 characters) in the targeted field
- `VeryLargeIntegersInNumericFields` - iterate through each numeric field and send requests with very large numbers (40000 characters) in the targeted field
- `VeryLargeUnicodeStringsInFields` - iterate through each field and send requests with very large random unicode values in the targeted field
- `WithinControlCharsInStringFields` - iterate through each field and send values containing unicode control chars
- `WithinSingleCodePointEmojisInStringFields` - iterate through each field and send values containing single code point emojis
- `WithinMultiCodePointEmojisInStringFields` - iterate through each field and send values containing multi code point emojis
- `XssInjectionInStringFields` - iterate through each string field and send XSS payloads to detect Cross-Site Scripting vulnerabilities
- `ZalgoTextInFields` - iterate through each field and send values containing zalgo text
- `ZeroWidthCharsInNamesFields` - iterate through each field and insert zero-width characters in the field names
- `ZeroWidthCharsInValuesFields` - iterate through each field and send values containing zero-width characters

## Type-coercion fuzzers

These fuzzers send values encoded as a different type to check whether the
service or its framework silently coerces input. They expect `4XX` by default;
use `--no-strictTypes` when the service intentionally accepts the coercion.

- `AlternativeDateTimeFormatsFields` - iterate through date-time fields and send ISO local date and offset-less local date-time formats
- `BooleanStringInBooleanFields` - send boolean values encoded as strings
- `BooleanValuesInStringFields` - send boolean values to string fields
- `EnumNumericOrdinalFields` - send numeric enum ordinals to enum fields
- `EpochNumberInDateTimeFields` - send epoch numbers to date-time fields
- `LeadingZerosInNumericFields` - send numeric values with leading zeros
- `NumericBooleansInBooleanFields` - send numeric boolean values
- `NumericStringsInNumericFields` - send numeric values encoded as strings
- `NumericValuesInStringFields` - send numeric values to string fields
- `ScientificNotationStringsInDecimalFields` - send scientific-notation numbers as strings
- `SingleElementArrayToScalarFields` - send primitive values wrapped in a single-element array
- `SmallNegativeAndInt32BoundaryFields` - iterate through integer and numeric fields and send `0`, `-1`, and `Integer.MAX_VALUE` boundary values
- `SpecialFloatTokensInNumericFields` - iterate through each number field and send IEEE-754 special float token values (`NaN`, `Infinity`, `-Infinity`)
- `WhitespacePaddedNumericFields` - send numeric values padded with whitespace

## Additional field fuzzers

- `DuplicateItemsInUniqueArraysFields` - send duplicate items in arrays declared with `uniqueItems: true`
- `FreeFormArrayItemsFields` - send hostile values in arrays whose items are unconstrained
- `FreeFormObjectFields` - send hostile keys and values in objects with unrestricted `additionalProperties`
- `LdapInjectionInStringFields` - send LDAP injection payloads to string fields to detect authentication bypass or unauthorized data access
- `NullElementsInArrayFields` - replace array fields with an array containing a `null` element (`[null]`)
- `OnlyRequiredFields` - send requests containing only the fields marked as required
- `QueryObjectAsRequestBody` - send complex query object parameters as JSON request bodies on non-body HTTP methods
- `SstiInjectionInStringFields` - send server-side template injection payloads to string fields
- `XxeInjectionInStringFields` - send XML External Entity payloads to string fields

You can run only these Fuzzers by supplying the `--checkFields` argument.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```
