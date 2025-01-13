package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.StringSchemaCollector;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

@Linter
@Singleton
public class StringSchemaLimitGlobalLinter extends AbstractSchemaLinter<Schema<?>> {
    /**
     * Constructor for the enum case linter.
     *
     * @param tcl                   the test case listener to report results
     * @param stringSchemaCollector the collector that gathers string schemas
     */
    protected StringSchemaLimitGlobalLinter(TestCaseListener tcl, StringSchemaCollector stringSchemaCollector) {
        super(tcl);
        context = createSchemaContext(stringSchemaCollector);
    }

    private SchemaLinterContext<Schema<?>> createSchemaContext(StringSchemaCollector stringSchemaCollector) {
        return new SchemaLinterContext<>(
                "Validate that each string schema specifies maxLength or enum",
                "All string schemas must specify maxLength or enum",
                stringSchemaCollector::getStringSchemas,
                (loc, data) -> loc.isGlobalLocation(), // filter for global components only
                schema -> (schema.getMaxLength() != null && schema.getMaxLength() > 0) || CatsUtil.isNotEmpty(schema.getEnum()),
                (loc, schema) -> "Schema at location '%s' does not specify maxLength or enum".formatted(loc.fqn()),
                "All string schemas have maxLength or enum defined",
                "String schemas without maxLength or enum found",
                data -> "global-string-schema-limit-linter"
        );
    }

    @Override
    public String description() {
        return "verifies that all string schemas specify either maxLength or enum";
    }
}
