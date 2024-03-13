package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.fields.base.ExpectOnly4XXBaseFieldsFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

/**
 * Fuzzer that sends very large  strings in string fields. Size of the large
 * strings is controlled by the {@code --largeStringsSize} argument.
 */
@Singleton
@FieldFuzzer
public class VeryLargeStringsInFieldsFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new VeryLargeStringsInFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param pa to get the size of the large strings
     */
    public VeryLargeStringsInFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cp);
        this.processingArguments = pa;
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "very large string values";
    }

    @Override
    protected List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        return Collections.singletonList(
                FuzzingStrategy.replace().withData(
                        StringGenerator.generateLargeString(processingArguments.getLargeStringsSize() / 4)));
    }

    @Override
    protected boolean shouldCheckForFuzzedValueMatchingPattern() {
        return false;
    }

    @Override
    protected boolean shouldMatchResponseSchema(FuzzingData data) {
        return HttpMethod.requiresBody(data.getMethod());
    }

    @Override
    protected boolean shouldMatchContentType(FuzzingData data) {
        return HttpMethod.requiresBody(data.getMethod());
    }

    @Override
    public String description() {
        return "iterate through each String field and send very large values (40000 characters)";
    }
}