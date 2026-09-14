---
sidebar_position: 90
description: Fuzzers that test implicit type conversion
---

# Type-Coercion Fuzzers

These fuzzers send a value using a representation different from the OpenAPI
schema, targeting frameworks that silently coerce request input. They are part
of the `type-coercion` profile and are included in a full run.

The default expectation is `4XX`, because strict validation should reject the
wrong representation. Use `--no-strictTypes` when the API intentionally
accepts the conversion and a `2XX` response is valid.

- `BooleanStringInBooleanFields` sends `true`/`false` as strings to boolean fields.
- `BooleanValuesInStringFields` sends boolean values to string fields.
- `EnumNumericOrdinalFields` sends numeric enum ordinals to enum fields.
- `EpochNumberInDateTimeFields` sends epoch numbers to date-time fields.
- `LeadingZerosInNumericFields` sends numeric values with leading zeros.
- `NumericBooleansInBooleanFields` sends `0`/`1` representations to boolean fields.
- `NumericStringsInNumericFields` sends numeric values as strings.
- `NumericValuesInStringFields` sends numeric values to string fields.
- `ScientificNotationStringsInDecimalFields` sends scientific-notation values as strings to decimal fields.
- `SingleElementArrayToScalarFields` wraps primitive values in a single-element array.
- `WhitespacePaddedNumericFields` sends numeric values padded with whitespace.

List the profile and all exact registered names with:

```bash
cats list --profiles
cats list --fuzzers
```
