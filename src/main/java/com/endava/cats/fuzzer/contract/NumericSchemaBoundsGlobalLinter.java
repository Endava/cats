package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.NumericSchemaCollector;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

/**
 * Linter that verifies all global numeric schemas in components/schemas specify minimum/maximum bounds or enum.
 */
@Linter
@Singleton
public class NumericSchemaBoundsGlobalLinter extends AbstractSchemaLinter<Schema<?>> {

    /**
     * Creates a new NumericSchemaBoundsGlobalLinter instance.
     *
     * @param tcl                    the test case listener
     * @param numericSchemaCollector the collector gathering numeric schemas
     */
    public NumericSchemaBoundsGlobalLinter(TestCaseListener tcl, NumericSchemaCollector numericSchemaCollector) {
        super(tcl);
        context = createSchemaContext(numericSchemaCollector);
    }

    private SchemaLinterContext<Schema<?>> createSchemaContext(NumericSchemaCollector numericSchemaCollector) {
        return new SchemaLinterContext<>(
                "Validate that each numeric schema specifies minimum/maximum bounds or enum",
                "All numeric schemas must specify minimum and maximum bounds or enum",
                numericSchemaCollector::getNumericSchemas,
                (loc, _) -> loc.isGlobalLocation(),
                NumericSchemaBoundsGlobalLinter::hasBoundsOrEnum,
                (loc, _) -> "Schema at location '%s' does not specify minimum/maximum bounds or enum".formatted(loc.fqn()),
                "All numeric schemas have minimum/maximum bounds or enum defined",
                "Numeric schemas without minimum/maximum bounds or enum found",
                _ -> "global-numeric-schema-bounds-linter"
        );
    }

    static boolean hasBoundsOrEnum(Schema<?> schema) {
        if (schema == null) {
            return false;
        }
        if (CatsUtil.isNotEmpty(schema.getEnum())) {
            return true;
        }
        boolean hasMin = schema.getMinimum() != null || schema.getExclusiveMinimumValue() != null;
        boolean hasMax = schema.getMaximum() != null || schema.getExclusiveMaximumValue() != null;
        return hasMin && hasMax;
    }

    @Override
    public String description() {
        return "verifies that all global numeric schemas specify minimum and maximum bounds or enum";
    }
}
