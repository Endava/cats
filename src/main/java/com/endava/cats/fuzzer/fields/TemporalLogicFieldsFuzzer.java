package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Attempts to send semantically invalid temporal values, like startDate > endDate, expiryDate in the past, etc.
 */
@Singleton
@FieldFuzzer
public class TemporalLogicFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final FieldsIteratorExecutor executor;
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^a-z0-9]");

    public TemporalLogicFieldsFuzzer(FieldsIteratorExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> allFields = data.getAllFieldsByHttpMethod();

        Map<String, Object> temporalViolations = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));

        for (String field : allFields) {
            String normalizedField = NORMALIZE_PATTERN.matcher(field.toLowerCase(Locale.ROOT)).replaceAll("");

            if (isStartDateField(normalizedField)) {
                temporalViolations.put(field, now.plusDays(5));
            } else if (isEndDateField(normalizedField)) {
                temporalViolations.put(field, now.minusDays(5));
            } else if (isExpiryField(normalizedField)) {
                temporalViolations.put(field, now.minusYears(1));
            } else if (isFutureOnlyField(normalizedField)) {
                temporalViolations.put(field, now.minusDays(1));
            } else if (isPastOnlyField(normalizedField)) {
                temporalViolations.put(field, now.plusYears(1));
            }
        }

        if (temporalViolations.isEmpty()) {
            logger.info("No temporal fields found in payload for semantic validation.");
            return;
        }

        executor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Inject semantically invalid timestamps like startDate > endDate or expired dates.")
                        .fuzzingData(data)
                        .fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .fieldFilter(temporalViolations::containsKey)
                        .fuzzValueProducer((schema, field) -> List.of(temporalViolations.get(field)))
                        .simpleReplaceField(true)
                        .logger(logger)
                        .fuzzer(this)
                        .build()
        );
    }

    private boolean isStartDateField(String field) {
        return field.toLowerCase(Locale.ROOT).matches(".*(start|from|validfrom).*date.*");
    }

    private boolean isEndDateField(String field) {
        return field.toLowerCase(Locale.ROOT).matches(".*(end|to|validto|until|expiry).*date.*");
    }

    private boolean isExpiryField(String field) {
        return field.toLowerCase(Locale.ROOT).matches(".*(expire|expiry).*date.*");
    }

    private boolean isFutureOnlyField(String field) {
        return field.toLowerCase(Locale.ROOT).matches(".*(scheduled|next|due).*date.*");
    }

    private boolean isPastOnlyField(String field) {
        return field.toLowerCase(Locale.ROOT).matches(".*(created|birth|issue).*date.*");
    }

    @Override
    public String description() {
        return "Sends semantically invalid date values (e.g., startDate after endDate, expired tokens, future birth dates) to verify if the backend enforces logical date constraints.";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
