package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that replaces JSON arrays with primitive values.
 */
@Singleton
@FieldFuzzer
public class ReplaceArraysWithPrimitivesFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    private static final String PRIMITIVE_VALUE = "cats_primitive_string";
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new ReplaceArraysWithPrimitivesFieldsFuzzer instance.
     *
     * @param ce the executor
     */
    public ReplaceArraysWithPrimitivesFieldsFuzzer(FieldsIteratorExecutor ce) {
        this(ce, null);
    }

    /**
     * Creates a new ReplaceArraysWithPrimitivesFieldsFuzzer instance with processing arguments.
     *
     * @param ce the executor
     * @param pa processing arguments
     */
    @Inject
    public ReplaceArraysWithPrimitivesFieldsFuzzer(FieldsIteratorExecutor ce, ProcessingArguments pa) {
        super(ce);
        this.processingArguments = pa;
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("array")
                .replaceWith("primitive")
                .skipMessage("Fuzzer only runs for arrays")
                .fieldFilter(field -> JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer((schema, field) -> List.of(PRIMITIVE_VALUE))
                .expectedResponseCodeResolver(field -> expectedResponseCodeFor(data, field))
                .build();
    }

    private ResponseCodeFamily expectedResponseCodeFor(FuzzingData data, String field) {
        if (processingArguments != null && !processingArguments.isStrictTypes() && isUnconstrainedStringArray(data, field)) {
            return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
        }
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    private static boolean isUnconstrainedStringArray(FuzzingData data, String field) {
        if (data == null || data.getRequestPropertyTypes() == null) {
            return false;
        }
        Schema<?> arraySchema = CatsModelUtils.resolveSchema(data.getRequestPropertyTypes().get(field), data.getSchemaMap());
        if (arraySchema == null || arraySchema.getItems() == null) {
            return false;
        }
        if (arraySchema.getMinItems() != null && arraySchema.getMinItems() > 1) {
            return false;
        }
        Schema<?> itemSchema = CatsModelUtils.resolveSchema(arraySchema.getItems(), data.getSchemaMap());
        return isCompatibleUnconstrainedString(itemSchema);
    }

    private static boolean isCompatibleUnconstrainedString(Schema<?> itemSchema) {
        return CatsModelUtils.isStringSchema(itemSchema)
                && !CatsModelUtils.isEnumSchema(itemSchema)
                && itemSchema.getConst() == null
                && itemSchema.getPattern() == null
                && itemSchema.getFormat() == null
                && (itemSchema.getMinLength() == null || itemSchema.getMinLength() <= PRIMITIVE_VALUE.length())
                && (itemSchema.getMaxLength() == null || itemSchema.getMaxLength() >= PRIMITIVE_VALUE.length());
    }
}
