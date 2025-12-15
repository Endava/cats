package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A fuzzer that tests API robustness by swapping start/end date values in date range fields.
 * It identifies field pairs that represent date ranges (like startDate/endDate, checkIn/checkOut)
 * and deliberately violates their logical relationship to verify error handling.
 * <p>
 * This is the temporal equivalent of {@link MinGreaterThanMaxFieldsFuzzer}.
 * </p>
 */
@Singleton
@FieldFuzzer
public class DateRangeInversionFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());
    private final SimpleExecutor simpleExecutor;

    private record DateRangePair(String startSuffix, String endSuffix) {
    }

    private static final List<DateRangePair> DATE_RANGE_PATTERNS = List.of(
            new DateRangePair("startdate", "enddate"),
            new DateRangePair("starttime", "endtime"),
            new DateRangePair("startdatetime", "enddatetime"),
            new DateRangePair("fromdate", "todate"),
            new DateRangePair("fromtime", "totime"),
            new DateRangePair("begindate", "enddate"),
            new DateRangePair("begindate", "finishdate"),
            new DateRangePair("checkindate", "checkoutdate"),
            new DateRangePair("checkin", "checkout"),
            new DateRangePair("departuredate", "arrivaldate"),
            new DateRangePair("departure", "arrival"),
            new DateRangePair("pickupdate", "dropoffdate"),
            new DateRangePair("pickup", "dropoff"),
            new DateRangePair("validfrom", "validto"),
            new DateRangePair("effectivefrom", "effectiveto"),
            new DateRangePair("createdat", "updatedat"),
            new DateRangePair("opendate", "closedate"),
            new DateRangePair("issuedate", "expirydate"),
            new DateRangePair("birthdate", "deathdate")
    );

    public DateRangeInversionFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<FieldPair> allPairs = new HashSet<>();
        for (DateRangePair pattern : DATE_RANGE_PATTERNS) {
            allPairs.addAll(findDatePairs(data, pattern));
        }

        int pairsProcessed = 0;
        for (FieldPair pair : allPairs) {
            if (processDatePair(pair.startField(), pair.endField(), data)) {
                pairsProcessed++;
            }
        }

        if (pairsProcessed == 0) {
            logger.skip("No date range pairs found in the request");
        }
    }

    private boolean processDatePair(String startField, String endField, FuzzingData data) {
        Object startValue = JsonUtils.getVariableFromJson(data.getPayload(), startField);
        Object endValue = JsonUtils.getVariableFromJson(data.getPayload(), endField);

        if (JsonUtils.isNotSet(String.valueOf(startValue)) || JsonUtils.isNotSet(String.valueOf(endValue))) {
            return false;
        }

        Schema<?> startSchema = data.getRequestPropertyTypes().get(startField);
        Schema<?> endSchema = data.getRequestPropertyTypes().get(endField);

        if (isNotDateField(startSchema, startField) || isNotDateField(endSchema, endField)) {
            return false;
        }

        String startStr = String.valueOf(startValue);
        String endStr = String.valueOf(endValue);

        String swappedStartValue;
        String swappedEndValue;

        OffsetDateTime startDateTime = parseDateTime(startStr);
        OffsetDateTime endDateTime = parseDateTime(endStr);

        if (startDateTime != null && endDateTime != null) {
            if (startDateTime.isBefore(endDateTime)) {
                swappedStartValue = endStr;
                swappedEndValue = startStr;
            } else {
                swappedStartValue = endDateTime.plusDays(10).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                swappedEndValue = startDateTime.minusDays(10).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
        } else {
            LocalDate startDate = parseDate(startStr);
            LocalDate endDate = parseDate(endStr);

            if (startDate == null || endDate == null) {
                return false;
            }

            if (startDate.isBefore(endDate)) {
                swappedStartValue = endStr;
                swappedEndValue = startStr;
            } else {
                swappedStartValue = endDate.plusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE);
                swappedEndValue = startDate.minusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
        }

        String mutatedPayloadWithStart = CatsUtil.justReplaceField(data.getPayload(), startField, swappedStartValue).json();
        String mutatedPayload = CatsUtil.justReplaceField(mutatedPayloadWithStart, endField, swappedEndValue).json();
        executeTest(mutatedPayload, data, startField, endField, swappedStartValue, swappedEndValue);
        return true;
    }

    private boolean isNotDateField(Schema<?> schema, String fieldName) {
        if (schema != null && (CatsModelUtils.isDateSchema(schema) || CatsModelUtils.isDateTimeSchema(schema))) {
            return false;
        }

        String sanitizedFieldName = PropertySanitizer.sanitize(fieldName);
        return !sanitizedFieldName.contains("date") && !sanitizedFieldName.contains("time");
    }

    private OffsetDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException _) {
            try {
                return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
            } catch (DateTimeParseException _) {
                return null;
            }
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException _) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException _) {
                return null;
            }
        }
    }

    private void executeTest(String mutatedPayload, FuzzingData data, String startField, String endField,
                             String startValue, String endValue) {
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Send a request where date field [%s]=%s is after date field [%s]=%s, violating temporal relationship"
                                .formatted(startField, startValue, endField, endValue))
                        .build()
        );
    }

    private Set<FieldPair> findDatePairs(FuzzingData data, DateRangePair pattern) {
        Set<String> fields = data.getAllFieldsByHttpMethod();
        Set<FieldPair> datePairs = new HashSet<>();

        for (String currentField : fields) {
            String sanitizedName = PropertySanitizer.sanitize(currentField);

            if (sanitizedName.endsWith(pattern.startSuffix)) {
                String expectedEndSuffix = pattern.endSuffix;
                fields.stream()
                        .filter(field -> PropertySanitizer.sanitize(field).endsWith(expectedEndSuffix))
                        .findFirst()
                        .ifPresent(pairField -> datePairs.add(new FieldPair(currentField, pairField)));
            }
        }

        return datePairs;
    }

    private record FieldPair(String startField, String endField) {
    }

    @Override
    public String description() {
        return "Sends a request where the start date field (e.g., startDate, checkIn) is set after the end date field (e.g., endDate, checkOut) to test temporal validation";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
