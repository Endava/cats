---
hide_table_of_contents: true
---

# Timezone And Locale Code Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `TimezoneAndLocaleCodeFieldsFuzzer` |
| **Log Key** | **TALCFF** |
| **Description** | Fuzzes non-enum timezone (`timezone`, `tz`, `zoneId`, `timezoneName`, `iana`, `utcOffset`), locale/language (`locale`, `language`, `lang`, `languageCode`, `localeCode`, `bcp47`), and currency/country code (`currency`, `currencyCode`, `countryCode`, `isoCountry`, `isoCurrency`) string fields with invalid IANA (`Invalid/Timezone`, `UTC+25:00`), BCP-47 (`xx-INVALID-999`, `en_US@currency=INVALID`), and ISO (`ZZZ`, `INVALID_ISO`) codes. |
| **Enabled by default?** | Yes (included in `ci`, `crash`, and `full` profiles) |
| **Target field types** | Non-enum string fields matching timezone, locale/language, or currency/country code names |
| **Expected result when fuzzed field is required** | `4XX` |
| **Expected result when fuzzed field is optional** | `4XX` |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Replaces eligible domain code fields with invalid IANA, BCP-47, or ISO codes to detect unhandled `ZoneRulesException`, `IllformedLocaleException`, `IllegalArgumentException` (`Currency.getInstance`), and missing domain validation. |
| **Conditions when this fuzzer will be skipped** | When a field is not a string, declares an explicit `enum`, or does not match timezone/locale/currency/country naming. |
| **HTTP methods that will be skipped** | `HEAD`, `TRACE` |
| **Reporting** | Reports `error` if the server accepts the invalid domain code with `2XX` or crashes with `5XX`; reports `success` when rejected with `4XX`. |
