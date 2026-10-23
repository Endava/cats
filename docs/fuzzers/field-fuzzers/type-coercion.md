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

| Full Fuzzer Name | Log Key | Enabled by default? | Target field types | Expected result | Fuzzing logic | Conditions when skipped | HTTP methods skipped | Reporting |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| `AlternativeDateTimeFormatsFieldsFuzzer` | N/A | Yes in the `type-coercion`, `coverage`, `crash`, and `full` profiles | `date-time` fields | `4XX` by default; `2XX` or `4XX` with `--no-strictTypes` | Sends ISO local date (`yyyy-MM-dd`) and offset-less local date-time (`yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd HH:mm:ss`) formats in `date-time` fields. | Empty payloads or requests without `date-time` fields. | `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `BooleanStringInBooleanFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Boolean fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends `true` and `false` as strings. | Non-boolean fields, enums, discriminators, and reference data. | `GET`, `DELETE`, `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `BooleanValuesInStringFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | String fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends boolean values to string fields. | Non-string fields, enums, discriminators, and reference data. | `GET`, `DELETE`, `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `EnumNumericOrdinalFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Enum fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends the numeric ordinal of an enum value. | Non-enum fields, unsupported enum values, discriminators, and reference data. | `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `EpochNumberInDateTimeFieldsFuzzer` | N/A | Yes in the `type-coercion`, `crash`, and `full` profiles | `date-time` fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends an epoch number to a date-time field. | Fields without the `date-time` format, discriminators, and reference data. | `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `LeadingZerosInNumericFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Numeric and integer fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends valid numeric values encoded with leading zeros. | Non-numeric fields, values that cannot be represented with leading zeros, and reference data. | `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `NumericBooleansInBooleanFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Boolean fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends `0` and `1` as boolean representations. | Non-boolean fields, enums, discriminators, and reference data. | `GET`, `DELETE`, `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `NumericStringsInNumericFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Numeric and integer fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends numeric values encoded as strings. | Non-numeric fields, enums, discriminators, and reference data. | `GET`, `DELETE`, `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `NumericValuesInStringFieldsFuzzer` | N/A | Yes in the `type-coercion`, `quick`, `ci`, `coverage`, `crash`, and `full` profiles | Request body string fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends unquoted numeric values (including 64-bit integers) to string fields. | Non-string fields, URL path/query parameters, enums, discriminators, and reference data. | `GET`, `DELETE`, `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `ScientificNotationStringsInDecimalFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Decimal and floating-point fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends scientific-notation values encoded as strings. | Non-decimal fields, unsupported formats, and reference data. | `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `SingleElementArrayToScalarFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Primitive fields | `4XX` by default; `2XX` with `--no-strictTypes` | Wraps a valid primitive value in a single-element array. | Non-primitive fields, discriminators, and reference data. | `GET`, `DELETE`, `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |
| `SmallNegativeAndInt32BoundaryFieldsFuzzer` | N/A | Yes in the `quick`, `ci`, `crash`, and `full` profiles | Integer and numeric fields (including query parameters) | `4XX` when the value violates declared schema `minimum`/`maximum` bounds (with `--strictTypes`); `2XX` or `4XX` otherwise | Sends `0`, `-1`, and `Integer.MAX_VALUE` (`2147483647`) to integer and numeric fields so values pass 32-bit deserialization and reach controller/service boundary checks. | Non-numeric fields, enums, discriminators, and reference data. | `HEAD`, `TRACE` | Follows normal CATS reporting against the expected response family. |
| `WhitespacePaddedNumericFieldsFuzzer` | N/A | Yes in the `type-coercion` and `full` profiles | Numeric and integer fields | `4XX` by default; `2XX` with `--no-strictTypes` | Sends numeric values padded with whitespace. | Non-numeric fields, values that cannot be padded, and reference data. | `HEAD`, `TRACE` | Follows normal CATS reporting against the configured expected response family. |

- `AlternativeDateTimeFormatsFields` sends ISO local date (`yyyy-MM-dd`) and offset-less local date-time (`yyyy-MM-dd'T'HH:mm:ss` and `yyyy-MM-dd HH:mm:ss`) strings to `date-time` fields.
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
- `SmallNegativeAndInt32BoundaryFields` sends `0`, `-1`, and `Integer.MAX_VALUE` (`2147483647`) to integer and numeric fields.
- `WhitespacePaddedNumericFields` sends numeric values padded with whitespace.

List the profile and all exact registered names with:

```bash
cats list --profiles
cats list --fuzzers
```
