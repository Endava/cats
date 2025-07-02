package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Inserts bidirectional-override control characters into String fields
 * to detect Trojan-Source style ambiguities and log-parsing bugs.
 */
@FieldFuzzer
@Singleton
public class BidirectionalOverrideFieldsFuzzer extends BaseReplaceFieldsFuzzer {

    private static final String RLO = "\u202E"; // RIGHT-TO-LEFT OVERRIDE
    private static final String PDF = "\u202C"; // POP DIRECTIONAL FORMATTING

    private final ProcessingArguments processingArguments;

    /**
     * Constructor for initializing common dependencies for fuzzing fields.
     *
     * @param ce the executor
     */
    public BidirectionalOverrideFieldsFuzzer(FieldsIteratorExecutor ce, ProcessingArguments pa) {
        super(ce);
        this.processingArguments = pa;
    }

    @Override
    public BaseReplaceFieldsContext getContext(FuzzingData data) {
        BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer = (schema, field) -> {
            String original = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), field));

            /* Variant 1 – prepend RLO to reverse entire string visually. */
            String v1 = RLO + original;

            /* Variant 2 – embed RLO/PDF after first char, reversing the tail. */
            String tail = original.length() > 1 ? original.substring(1) : "";
            String v2 = original.charAt(0) + RLO + new StringBuilder(tail).reverse() + PDF;

            /* Variant 3 – append RLO (can flip the following text in logs). */
            String v3 = original + RLO;

            return List.of(v1, v2, v3);
        };

        return BaseReplaceFieldsContext.builder()
                .replaceWhat("string")
                .replaceWith("bidirectional-override characters")
                .skipMessage("Fuzzer only runs for String non-enum fields")
                .fieldFilter(field -> {
                    Schema<?> schema = data.getRequestPropertyTypes().get(field);
                    return CatsModelUtils.isStringSchema(schema) && !CatsModelUtils.isEnumSchema(schema) && JsonUtils.isFieldInJson(data.getPayload(), field);
                })
                .fuzzValueProducer(fuzzValueProducer)
                .expectedResponseCode(processingArguments.isSanitizeFirst() ? ResponseCodeFamilyPredefined.TWOXX : ResponseCodeFamilyPredefined.FOURXX)
                .replaceRefData(true)
                .build();
    }
}