package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.ArraySchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

/**
 * Linter that verifies all global array schemas in components/schemas specify {@code maxItems}.
 */
@Linter
@Singleton
public class ArraySchemaLimitGlobalLinter extends AbstractSchemaLinter<Schema<?>> {

    /**
     * Creates a new ArraySchemaLimitGlobalLinter instance.
     *
     * @param tcl                  the test case listener
     * @param arraySchemaCollector the collector gathering array schemas
     */
    public ArraySchemaLimitGlobalLinter(TestCaseListener tcl, ArraySchemaCollector arraySchemaCollector) {
        super(tcl);
        context = createSchemaContext(arraySchemaCollector);
    }

    private SchemaLinterContext<Schema<?>> createSchemaContext(ArraySchemaCollector arraySchemaCollector) {
        return new SchemaLinterContext<>(
                "Validate that each array schema specifies maxItems",
                "All array schemas must specify maxItems",
                arraySchemaCollector::getArraySchemas,
                (loc, _) -> loc.isGlobalLocation(),
                schema -> schema != null && schema.getMaxItems() != null && schema.getMaxItems() > 0,
                (loc, _) -> "Schema at location '%s' does not specify maxItems".formatted(loc.fqn()),
                "All array schemas have maxItems defined",
                "Array schemas without maxItems found",
                _ -> "global-array-schema-limit-linter"
        );
    }

    @Override
    public String description() {
        return "verifies that all global array schemas specify maxItems";
    }
}
