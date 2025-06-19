package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.fuzzer.contract.base.AbstractEnumCaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.EnumCollector;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Linter that checks enum-case consistency for global components/schemas.
 * It filters enums based on the absence of a method in the SchemaLocation.
 */
@Linter
@Singleton
public class EnumCaseGlobalLinter extends AbstractEnumCaseLinter {

    public EnumCaseGlobalLinter(TestCaseListener tcl, NamingArguments namingArguments, EnumCollector enumCollector) {
        super(tcl, namingArguments, enumCollector);
    }

    @Override
    protected Map<SchemaLocation, List<String>> selectEnums(FuzzingData data) {
        return enumCollector.getEnums().entrySet().stream()
                .filter(e -> e.getKey().method() == null)          // global components
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "global-enum-case-linter";
    }

    @Override
    public String description() {
        return "Verifies that all string enum values follow a consistent case pattern across components/schemas.";
    }
}