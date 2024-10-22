package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Fuzzer that overflows arrays.
 */
@Singleton
@FieldFuzzer
public class OverflowArraySizeFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    private final ProcessingArguments processingArguments;

    /**
     * Creates a new OverflowArraySizeFieldsFuzzer instance.
     *
     * @param ce the executor
     * @param pa to get the size of the overflow size
     */
    public OverflowArraySizeFieldsFuzzer(FieldsIteratorExecutor ce, ProcessingArguments pa) {
        super(ce);
        this.processingArguments = pa;
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        BiFunction<Schema<?>, String, List<Object>> fuzzValueProducer = (schema, string) -> {
            int size = schema.getMaxItems() != null ? schema.getMaxItems() : processingArguments.getLargeStringsSize();
            logger.info("Fuzzing field [{}] with an array of size [{}]", string, size);

            String fieldValue = JsonUtils.serialize(JsonUtils.getVariableFromJson(data.getPayload(), string + "[0]"));
            int repetitions = CatsUtil.getMaxArraySizeBasedOnFieldsLength(Optional.ofNullable(fieldValue).orElse("ARRAY"), size);

            return List.of("[" + StringUtils.repeat(fieldValue, ",", repetitions) + "]");
        };

        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("array")
                .replaceWith("overflow array")
                .skipMessage("Fuzzer only runs for arrays")
                .fieldFilter(field -> JsonUtils.isArray(data.getPayload(), field))
                .fuzzValueProducer(fuzzValueProducer)
                .build();
    }
}