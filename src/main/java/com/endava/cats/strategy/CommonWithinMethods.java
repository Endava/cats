package com.endava.cats.strategy;

import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

public final class CommonWithinMethods {

    private CommonWithinMethods() {
        //ntd
    }

    public static List<FuzzingStrategy> getFuzzingStrategies(Schema<?> fuzzedFieldSchema, List<String> invisibleChars, boolean maintainSize) {
        if (!"string".equalsIgnoreCase(fuzzedFieldSchema.getType())
                || "binary".equalsIgnoreCase(fuzzedFieldSchema.getFormat())
                || "byte".equalsIgnoreCase(fuzzedFieldSchema.getFormat())) {
            return Collections.singletonList(FuzzingStrategy.skip().withData("Field does not match String schema or has binary/byte format"));
        }
        String initialValue = StringGenerator.generateValueBasedOnMinMax(fuzzedFieldSchema);

        /*independent of the supplied strategy, we still maintain sizes for enums*/
        final boolean insertWithoutReplace = !maintainSize || !CollectionUtils.isEmpty(fuzzedFieldSchema.getEnum());

        return invisibleChars
                .stream().map(value -> FuzzingStrategy.replace().withData(insertInTheMiddle(initialValue, value, insertWithoutReplace))).toList();
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
