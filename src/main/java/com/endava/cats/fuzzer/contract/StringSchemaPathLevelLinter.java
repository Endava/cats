package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.StringSchemaCollector;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;

@Linter
@Singleton
public class StringSchemaPathLevelLinter extends AbstractSchemaLinter<Schema<?>> {

    /**
     * Constructor for the StringSchemaPathLevelLinter.
     *
     * @param tcl                   the test case listener to report results
     * @param stringSchemaCollector the collector that gathers string schemas
     */
    protected StringSchemaPathLevelLinter(TestCaseListener tcl, StringSchemaCollector stringSchemaCollector) {
        super(tcl);
        context = createSchemaContext(stringSchemaCollector);
    }

    private SchemaLinterContext<Schema<?>> createSchemaContext(StringSchemaCollector stringSchemaCollector) {
        return new SchemaLinterContext<>(
                "Validate that each string schema specifies maxLength or enum",
                "All string schemas must specify maxLength or enum",
                stringSchemaCollector::getStringSchemas,
                (loc, data) -> loc.matchesPathAndMethod(data.getPath(), data.getMethod()), // filter for inline schemas of current path/method
                schema -> (schema.getMaxLength() != null && schema.getMaxLength() > 0) || CollectionUtils.isNotEmpty(schema.getEnum()),
                (loc, schema) -> "Schema at location '%s' does not specify maxLength or enum".formatted(loc.fqn()),
                "All string schemas have maxLength or enum defined",
                "String schemas without maxLength or enum found",
                data -> data.getPath() + data.getMethod()
        );
    }

    @Override
    public String description() {
        return "verifies that string schemas specify either maxLength or enum for inline schemas of the current path/method";
    }
}
