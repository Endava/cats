package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.fuzzer.contract.base.AbstractSchemaLinter;
import com.endava.cats.fuzzer.contract.base.SchemaLinterContext;
import com.endava.cats.openapi.handler.collector.EnumCollector;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Linter that checks enum-case consistency for global components/schemas.
 * It filters enums based on the absence of a method in the SchemaLocation.
 */
@Linter
@Singleton
public class EnumCaseGlobalLinter extends AbstractSchemaLinter<List<String>> {


    public EnumCaseGlobalLinter(TestCaseListener tcl, NamingArguments namingArguments, EnumCollector enumCollector) {
        super(tcl);
        context = createContext(namingArguments, enumCollector);
    }

    private SchemaLinterContext<List<String>> createContext(NamingArguments namingArguments, EnumCollector enumCollector) {
        return new SchemaLinterContext<>(
                "Validate that every string enum value complies with the configured case format (%s)".formatted(namingArguments.getEnumsNaming().getPattern().pattern()),
                "All enum values should match the pattern %s".formatted(namingArguments.getEnumsNaming().getPattern()),
                enumCollector::getEnums,
                (loc, data) -> loc.isGlobalLocation(), // filter for global components
                list -> list.stream().allMatch(enumValue -> namingArguments.getEnumsNaming().getPattern().matcher(enumValue).matches()),
                (loc, values) -> "Schema at location '%s' contains enum value(s) %s that violate naming convention"
                        .formatted(loc.fqn(), values),
                "All enum values respect the case convention",
                "Enum-case mismatches detected",
                data -> "global-enum-case-linter");
    }

    @Override
    public String description() {
        return "verifies that all string enum values follow a consistent case pattern across components/schemas";
    }
}