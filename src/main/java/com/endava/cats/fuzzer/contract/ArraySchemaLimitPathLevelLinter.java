package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.ArraySchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

/**
 * Linter that verifies all inline array schemas for the current path and HTTP method specify {@code maxItems}.
 */
@Linter
@Singleton
public class ArraySchemaLimitPathLevelLinter extends AbstractSchemaLinter<Schema<?>> {

    /**
     * Creates a new ArraySchemaLimitPathLevelLinter instance.
     *
     * @param tcl                  the test case listener
     * @param arraySchemaCollector the collector gathering array schemas
     */
    public ArraySchemaLimitPathLevelLinter(TestCaseListener tcl, ArraySchemaCollector arraySchemaCollector) {
        super(tcl);
        context = createSchemaContext(arraySchemaCollector);
    }

    private SchemaLinterContext<Schema<?>> createSchemaContext(ArraySchemaCollector arraySchemaCollector) {
        return new SchemaLinterContext<>(
                "Validate that each inline array schema specifies maxItems",
                "All inline array schemas must specify maxItems",
                arraySchemaCollector::getArraySchemas,
                (loc, data) -> loc.matchesPathAndMethod(data.getPath(), data.getMethod()),
                schema -> schema != null && schema.getMaxItems() != null && schema.getMaxItems() > 0,
                (loc, _) -> "Schema at location '%s' does not specify maxItems".formatted(loc.fqn()),
                "All array schemas have maxItems defined",
                "Array schemas without maxItems found",
                data -> data.getPath() + data.getMethod()
        );
    }

    @Override
    public String description() {
        return "verifies that array schemas specify maxItems for inline schemas of the current path/method";
    }
}
