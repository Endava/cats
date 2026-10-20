package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Fuzzer that sends date-only ({@code yyyy-MM-dd}) and offset-less local date-time
 * ({@code yyyy-MM-dd'T'HH:mm:ss} and {@code yyyy-MM-dd HH:mm:ss}) strings in {@code date-time} fields.
 */
@Singleton
@FieldFuzzer
public class AlternativeDateTimeFormatsFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new AlternativeDateTimeFormatsFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public AlternativeDateTimeFormatsFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        this.simpleExecutor = simpleExecutor;
        this.processingArguments = processingArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        List<String> dateTimeFields = data.getAllFieldsByHttpMethod().stream()
                .filter(field -> CatsModelUtils.isDateTimeSchema(data.getRequestPropertyTypes().get(field)))
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field))
                .toList();
        if (dateTimeFields.isEmpty()) {
            logger.skip("No date-time fields found in the request");
            return;
        }
        String baseDate = LocalDate.now(ZoneOffset.UTC).toString();
        List<String> formats = List.of(baseDate, baseDate + "T10:15:30", baseDate + " 10:15:30");
        for (String formattedValue : formats) {
            String fuzzedPayload = data.getPayload();
            for (String field : dateTimeFields) {
                fuzzedPayload = CatsUtil.justReplaceField(fuzzedPayload, field, formattedValue).json();
            }
            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(processingArguments.isStrictTypes()
                                    ? ResponseCodeFamilyPredefined.FOURXX : ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                            .fuzzingData(data)
                            .logger(logger)
                            .scenario("Send alternative date/date-time format [%s] in date-time fields %s"
                                    .formatted(formattedValue, dateTimeFields))
                            .fuzzer(this)
                            .payload(fuzzedPayload)
                            .mutationTargets(RequestTargetResolver.resolvePayloadFields(data, dateTimeFields))
                            .build());
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterate through date-time fields and send ISO local date and offset-less local date-time formats";
    }
}
