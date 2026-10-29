package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.NumericSchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

/**
 * Linter that verifies all inline numeric schemas for the current path and HTTP method specify minimum/maximum bounds or enum.
 */
@Linter
@Singleton
public class NumericSchemaBoundsPathLevelLinter extends AbstractSchemaLinter<Schema<?>> {

    /**
     * Creates a new NumericSchemaBoundsPathLevelLinter instance.
     *
     * @param tcl                    the test case listener
     * @param numericSchemaCollector the collector gathering numeric schemas
     */
    public NumericSchemaBoundsPathLevelLinter(TestCaseListener tcl, NumericSchemaCollector numericSchemaCollector) {
        super(tcl);
        context = createSchemaContext(numericSchemaCollector);
    }

    private SchemaLinterContext<Schema<?>> createSchemaContext(NumericSchemaCollector numericSchemaCollector) {
        return new SchemaLinterContext<>(
                "Validate that each inline numeric schema specifies minimum/maximum bounds or enum",
                "All inline numeric schemas must specify minimum and maximum bounds or enum",
                numericSchemaCollector::getNumericSchemas,
                (loc, data) -> loc.matchesPathAndMethod(data.getPath(), data.getMethod()),
                NumericSchemaBoundsGlobalLinter::hasBoundsOrEnum,
                (loc, _) -> "Schema at location '%s' does not specify minimum/maximum bounds or enum".formatted(loc.fqn()),
                "All numeric schemas have minimum/maximum bounds or enum defined",
                "Numeric schemas without minimum/maximum bounds or enum found",
                data -> data.getPath() + data.getMethod()
        );
    }

    @Override
    public String description() {
        return "verifies that numeric schemas specify minimum and maximum bounds or enum for inline schemas of the current path/method";
    }
}
