package com.endava.cats.model;

import com.endava.cats.model.generator.PayloadGenerator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class CommonWithinMethods {

    private CommonWithinMethods() {
        //ntd
    }

    public static List<FuzzingStrategy> getFuzzingStrategies(FuzzingData data, String fuzzedField, List<String> invisibleChars, boolean maintainSize) {
        Schema<?> fuzzedFieldSchema = data.getRequestPropertyTypes().get(fuzzedField);
        if (!StringSchema.class.isAssignableFrom(fuzzedFieldSchema.getClass())) {
            return Collections.singletonList(FuzzingStrategy.skip().withData("Field does not match String schema"));
        }
        String initialValue = PayloadGenerator.generateValueBasedOnMinMAx(fuzzedFieldSchema);

        /*independent of the supplied strategy, we still maintain sizes for enums*/
        final boolean insertWithoutReplace = !maintainSize || !CollectionUtils.isEmpty(fuzzedFieldSchema.getEnum());

        return invisibleChars
                .stream().map(value -> FuzzingStrategy.replace().withData(insertInTheMiddle(initialValue, value, insertWithoutReplace)))
                .collect(Collectors.toList());
    }

    public static String insertInTheMiddle(String value, String whatToInsert, boolean insertWithoutReplace) {
        int position = value.length() / 2;
        int whatToInsertLength = Math.min(value.length(), whatToInsert.length());
        return value.substring(0, position - (insertWithoutReplace ? 0 : whatToInsertLength / 2)) + whatToInsert + value.substring(position + (insertWithoutReplace ? 0 : whatToInsertLength / 2));
    }

    public static FuzzingStrategy getTextBasedOnMaxSize(Schema<?> fuzzedFieldSchema, String text) {
        int max = getMax(fuzzedFieldSchema, text);

        return FuzzingStrategy.replace().withData(text.substring(0, max));
    }


    private static int getMax(Schema<?> fuzzedFieldSchema, String text) {
        if (fuzzedFieldSchema.getMaxLength() == null) {
            return text.length();
        }
        return fuzzedFieldSchema.getMaxLength() >= text.length() ? text.length() : fuzzedFieldSchema.getMaxLength();
    }
}
