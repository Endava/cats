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
 * Linter that checks enum-case consistency only for inline schemas of the current path/method.
 * It filters enums based on the method and path of the FuzzingData.
 */
@Linter
@Singleton
public class EnumCasePathLevelLinter extends AbstractSchemaLinter<List<String>> {


    public EnumCasePathLevelLinter(TestCaseListener tcl, NamingArguments namingArguments, EnumCollector enumCollector) {
        super(tcl);
        context = createContext(namingArguments, enumCollector);
    }

    private SchemaLinterContext<List<String>> createContext(NamingArguments namingArguments, EnumCollector enumCollector) {
        return new SchemaLinterContext<>(
                "Validate that every string enum value complies with the configured case format (%s)".formatted(namingArguments.getEnumsNaming().getPattern().pattern()),
                "All enum values should match the pattern %s".formatted(namingArguments.getEnumsNaming().getPattern()),
                enumCollector::getEnums,
                (loc, data) -> loc.matchesPathAndMethod(data.getPath(), data.getMethod()), // filter for inline schemas of current path/method
                list -> list.stream().allMatch(enumValue -> namingArguments.getEnumsNaming().getPattern().matcher(enumValue).matches()),
                (loc, values) -> loc + " => " + values,
                "All enum values respect the case convention",
                "Enum-case mismatches detected",
                data -> data.getPath() + data.getMethod()
        );
    }

    @Override
    public String description() {
        return "verifies enum-case consistency only for inline schemas of the current path/method";
    }
}