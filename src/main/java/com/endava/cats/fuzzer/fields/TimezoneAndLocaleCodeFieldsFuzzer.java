package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Fuzzes non-enum timezone, locale/language, and currency/country code string fields with
 * invalid IANA, BCP-47, and ISO codes to detect unhandled runtime parser exceptions and missing domain validation.
 */
@Singleton
@FieldFuzzer
public class TimezoneAndLocaleCodeFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    private static final Set<String> TIMEZONE_FIELDS = Set.of(
            "timezone", "tz", "zoneid", "timezonename", "iana", "utcoffset"
    );
    private static final Set<String> LOCALE_FIELDS = Set.of(
            "locale", "language", "lang", "languagecode", "localecode", "bcp47"
    );
    private static final Set<String> CURRENCY_OR_COUNTRY_FIELDS = Set.of(
            "currency", "currencycode", "countrycode", "isocountry", "isocurrency"
    );

    private static final List<Object> INVALID_TIMEZONE_VALUES = List.of("Invalid/Timezone", "UTC+25:00");
    private static final List<Object> INVALID_LOCALE_VALUES = List.of("xx-INVALID-999", "en_US@currency=INVALID");
    private static final List<Object> INVALID_ISO_CODE_VALUES = List.of("ZZZ", "INVALID_ISO");

    /**
     * Creates a new TimezoneAndLocaleCodeFieldsFuzzer instance.
     *
     * @param executor the fields iterator executor
     */
    public TimezoneAndLocaleCodeFieldsFuzzer(FieldsIteratorExecutor executor) {
        super(executor);
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsContext.builder()
                .replaceWhat("timezone, locale, currency, and country code")
                .replaceWith("invalid IANA, BCP-47, and ISO code")
                .skipMessage("Fuzzer only runs for non-enum timezone, locale, currency, or country code string fields")
                .fieldFilter(field -> isEligibleField(data, field))
                .fuzzValueProducer((_, field) -> payloadsForField(field))
                .build();
    }

    static boolean isEligibleField(FuzzingData data, String field) {
        if (data == null || data.getRequestPropertyTypes() == null) {
            return false;
        }
        Schema<?> schema = data.getRequestPropertyTypes().get(field);
        return CatsModelUtils.isStringSchema(schema)
                && !CatsModelUtils.isEnumSchema(schema)
                && isTargetFieldName(field)
                && JsonUtils.isFieldInJson(data.getPayload(), field);
    }

    static boolean isTargetFieldName(String field) {
        String leaf = WordUtils.normalizeLeaf(field);
        return TIMEZONE_FIELDS.contains(leaf)
                || LOCALE_FIELDS.contains(leaf)
                || CURRENCY_OR_COUNTRY_FIELDS.contains(leaf);
    }

    static List<Object> payloadsForField(String field) {
        String leaf = WordUtils.normalizeLeaf(field);
        if (TIMEZONE_FIELDS.contains(leaf)) {
            return INVALID_TIMEZONE_VALUES;
        }
        if (LOCALE_FIELDS.contains(leaf)) {
            return INVALID_LOCALE_VALUES;
        }
        return INVALID_ISO_CODE_VALUES;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
