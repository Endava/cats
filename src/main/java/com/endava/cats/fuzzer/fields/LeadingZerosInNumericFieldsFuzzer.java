package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseTypeCoercionFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Fuzzer that sends numeric field values as strings with leading zeros.
 * <p>
 * This tests how APIs handle type coercion and validation when receiving
 * string representations of numbers with leading zeros (e.g., "00123" instead of 123).
 * </p>
 * <p>
 * Leading zeros in numeric strings can cause issues:
 * <ul>
 *   <li>Some parsers interpret leading zeros as octal numbers (0123 = 83 in decimal)</li>
 *   <li>Type coercion may silently accept invalid input</li>
 *   <li>String comparison vs numeric comparison issues</li>
 *   <li>Database storage inconsistencies</li>
 * </ul>
 * </p>
 */
@Singleton
@FieldFuzzer
public class LeadingZerosInNumericFieldsFuzzer extends BaseTypeCoercionFuzzer {
    private static final List<String> ZERO_PREFIXES = List.of("0", "00", "000");

    /**
     * Creates a new LeadingZerosInNumericFieldsFuzzer instance.
     *
     * @param simpleExecutor      the executor used to run the fuzz logic
     * @param processingArguments the processing arguments
     */
    public LeadingZerosInNumericFieldsFuzzer(SimpleExecutor simpleExecutor, ProcessingArguments processingArguments) {
        super(simpleExecutor, processingArguments);
    }

    @Override
    protected BiFunction<String, FuzzingData, Boolean> fieldsFilterFunction() {
        return (field, data) -> {
            Schema<?> schema = data.getRequestPropertyTypes().get(field);
            return CatsModelUtils.isNumberSchema(schema) || CatsModelUtils.isIntegerSchema(schema);
        };
    }

    @Override
    protected List<Object> getFuzzedValues(Object currentValue) {
        return ZERO_PREFIXES.stream().map(zero -> (Object) (zero + currentValue)).toList();
    }

    @Override
    protected String getOriginalType() {
        return "number";
    }

    @Override
    protected String getFuzzedType() {
        return "zero-prefixed number";
    }

}