package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.PayloadFormat;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Stream;

/** Sends exact JSON number lexemes and their quoted counterparts, accepting controlled acceptance or rejection. */
@Singleton
@FieldFuzzer
public class NumericParserBoundaryFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor executor;
    private final RobustnessResponseReporter reporter;
    private final FilesArguments files;

    public NumericParserBoundaryFieldsFuzzer(SimpleExecutor executor, FilesArguments files, RobustnessResponseReporter reporter) {
        this.executor = executor;
        this.reporter = reporter;
        this.files = files;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (isApplicableTo(data) && (isNumeric(data.getReqSchema()) || JsonUtils.isValidJson(data.getPayload()))) {
            fields(data).forEach(field -> executeField(data, field));
        }
    }

    private Stream<String> fields(FuzzingData data) {
        List<String> removed = FilesArguments.getFieldsToRemoveFromRefData(
                FilesArguments.resolveRefData(files, data.getPath(), data.getMethod()));
        if (isNumeric(data.getReqSchema())) {
            return removed.contains("$") ? Stream.empty() : Stream.of("$");
        }
        return data.getAllFieldsByHttpMethod().stream().filter(field -> !removed.contains(field))
                .filter(field -> isNumeric(data.getRequestPropertyTypes().get(field)))
                .filter(field -> JsonUtils.isFieldInJson(data.getPayload(), field));
    }

    private static boolean isNumeric(Schema<?> schema) {
        return schema != null && !CatsModelUtils.isEnumSchema(schema)
                && (CatsModelUtils.isNumberSchema(schema) || CatsModelUtils.isIntegerSchema(schema));
    }

    private void executeField(FuzzingData data, String field) {
        RawJsonFieldTemplate template = RawJsonFieldTemplate.of(data.getPayload(), field);
        NumericParserInputs.values().forEach(value -> executor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data).fuzzer(this).logger(logger).scenario("Send exact numeric parser input [%s] in [%s].".formatted(value, field))
                .payload(template.replaceWith(value)).validJson(false).replaceRefData(false)
                .mutationTarget("$".equals(field) ? RequestTarget.requestBody() : RequestTarget.body(field))
                .responseProcessor((response, context) -> reporter.report(logger, context, response)).expectedResponseCode(RobustnessResponseReporter.expectedResponses()).matchResponseResult(false).matchResponseContentType(false).build()));
    }

    @Override
    public boolean isApplicableTo(FuzzingData data) {
        return data.getPayloadFormat() == PayloadFormat.JSON && data.getMethod() != null && HttpMethod.requiresBody(data.getMethod());
    }

    @Override
    public String description() {
        return "checks numeric parser robustness using exact raw and quoted decimals, exponent boundaries and malformed number syntax";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
