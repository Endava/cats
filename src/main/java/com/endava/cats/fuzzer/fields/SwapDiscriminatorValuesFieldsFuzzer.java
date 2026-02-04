package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.fields.base.BaseReplaceFieldsFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.JsonUtils;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Fuzzer that swaps discriminator values in fields.
 * It replaces the current discriminator value with another value from the set of available discriminator values.
 */
@Singleton
@FieldFuzzer
public class SwapDiscriminatorValuesFieldsFuzzer extends BaseReplaceFieldsFuzzer {
    private final CatsGlobalContext catsGlobalContext;

    /**
     * Constructor for initializing common dependencies for fuzzing fields.
     *
     * @param ce the executor
     */
    protected SwapDiscriminatorValuesFieldsFuzzer(FieldsIteratorExecutor ce, CatsGlobalContext catsGlobalContext) {
        super(ce);
        this.catsGlobalContext = catsGlobalContext;
    }

    @Override
    public BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext getContext(FuzzingData data) {
        Predicate<String> isFieldInJson = field -> JsonUtils.isFieldInJson(data.getPayload(), field);
        Predicate<String> isFieldDiscriminator = catsGlobalContext::isDiscriminator;

        return BaseReplaceFieldsFuzzer.BaseReplaceFieldsContext.builder()
                .replaceWhat("discriminator")
                .replaceWith("swapped values")
                .skipMessage("Fuzzer only runs for discriminator fields")
                .fieldFilter(isFieldDiscriminator.and(isFieldInJson))
                .fuzzValueProducer((schema, field) -> {
                    String oldValue = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), field));
                    return catsGlobalContext.getDiscriminatorValues().getOrDefault(field, Set.of()).stream()
                            .filter(Predicate.not(oldValue::equals))
                            .toList();
                })
                .build();
    }
}