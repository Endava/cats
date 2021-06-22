package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.generator.format.FormatGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extend this class to provide concrete boundary values to be used for fuzzing.
 * The assumption is that expected response is a 4XX code when sending out of boundary values for the fuzzed fields.
 */
public abstract class BaseBoundaryFieldFuzzer extends ExpectOnly4XXBaseFieldsFuzzer {

    @Autowired
    protected BaseBoundaryFieldFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    public String typeOfDataSentToTheService() {
        return "outside the boundary values";
    }

    @Override
    public List<FuzzingStrategy> getFieldFuzzingStrategy(FuzzingData data, String fuzzedField) {
        Schema<?> schema = data.getRequestPropertyTypes().get(fuzzedField);

        if (this.fuzzedFieldHasAnAssociatedSchema(schema)) {
            logger.info("Field [{}] schema is [{}] and type [{}]", fuzzedField, schema.getClass().getSimpleName(), schema.getType());

            if (this.isFieldFuzzable(fuzzedField, data) && this.fuzzerGeneratedBoundaryValue(schema)) {
                logger.start("[{}]. Start fuzzing...", getSchemasThatTheFuzzerWillApplyTo().stream().map(Class::getSimpleName).collect(Collectors.toSet()));
                return Collections.singletonList(FuzzingStrategy.replace().withData(this.getBoundaryValue(schema)));
            } else if (!this.hasBoundaryDefined(fuzzedField, data)) {
                logger.skip("Boundaries not defined. Will skip fuzzing...");
                return Collections.singletonList(FuzzingStrategy.skip().withData("No LEFT or RIGHT boundary info within the contract!"));
            } else if (!this.isStringFormatRecognizable(schema) && isRequestSchemaMatchingFuzzerType(schema)) {
                logger.skip("String format not supplied or not recognized [{}]", schema.getFormat());
                return Collections.singletonList(FuzzingStrategy.skip().withData("String format not supplied or not recognized!"));
            } else {
                logger.skip("Not [{}]. Will skip fuzzing...", getSchemasThatTheFuzzerWillApplyTo().stream().map(Class::getSimpleName).collect(Collectors.toSet()));
            }
        }
        return Collections.singletonList(FuzzingStrategy.skip().withData("Data type not matching " + getSchemasThatTheFuzzerWillApplyTo().stream().map(Class::getSimpleName).collect(Collectors.toSet())));
    }

    private boolean fuzzerGeneratedBoundaryValue(Schema<?> schema) {
        return this.getBoundaryValue(schema) != null;
    }

    /**
     * Checks if the current field is fuzzable by the boundary fuzzers. The following cases might skip the current fuzzer:
     * <ol>
     *     <li>the fuzzed field schema is not fuzzable by the current fuzzer. Each boundary Fuzzer provides a list with all applicable schemas.</li>
     *     <li>there is no boundary defined for the current field. This is also specific for each Fuzzer</li>
     *     <li>the string format is not recognizable. Check {@link FormatGenerator} for supported formats</li>
     * </ol>
     *
     * @param fuzzedField the current fuzzed field
     * @param data        the current FuzzingData
     * @return true if the field is fuzzble, false otherwise
     */
    private boolean isFieldFuzzable(String fuzzedField, FuzzingData data) {
        Schema schema = data.getRequestPropertyTypes().get(fuzzedField);
        return this.isRequestSchemaMatchingFuzzerType(schema) && (this.hasBoundaryDefined(fuzzedField, data) || this.isStringFormatRecognizable(schema));
    }

    private boolean isStringFormatRecognizable(Schema schema) {
        return FormatGenerator.from(schema.getFormat()).compareTo(FormatGenerator.SKIP) != 0;
    }

    private boolean fuzzedFieldHasAnAssociatedSchema(Schema schema) {
        return schema != null;
    }

    private boolean isRequestSchemaMatchingFuzzerType(Schema schema) {
        return getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(currentSchema1 -> currentSchema1.isAssignableFrom(schema.getClass())) ||
                (schema.getType() != null && getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(currentSchema2 -> currentSchema2.getSimpleName().toLowerCase().startsWith(schema.getType())));
    }

    /**
     * Override this to provide information about which Schema the Fuzzer is applicable to.
     * For example if the current Schema is StringSchema, but the Fuzzer apply to Integer values then the Fuzzer will get skipped.
     *
     * @return schema type for which the Fuzzer will apply.
     */
    public abstract List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo();

    /**
     * The value that will be used for fuzzing.
     *
     * @param schema used to extract boundary information
     * @return a value to be used for fuzzing
     */
    public abstract String getBoundaryValue(Schema schema);

    /**
     * Override this to provide information about whether the current field has boundaries defined or not. For example a String
     * field without minLength defined is not considered to have a left boundary.
     *
     * @param fuzzedField used to extract boundary information
     * @param data        FuzzingData constructed by CATS
     * @return true if the filed has a boundary defined or false otherwise
     */
    public abstract boolean hasBoundaryDefined(String fuzzedField, FuzzingData data);
}
