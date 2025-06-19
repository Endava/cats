package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.fuzzer.contract.base.AbstractEnumCaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.openapi.handler.collector.EnumCollector;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Linter that checks enum-case consistency only for inline schemas of the current path/method.
 * It filters enums based on the method and path of the FuzzingData.
 */
@Linter
@Singleton
public class EnumCasePathLevelLinter extends AbstractEnumCaseLinter {

    public EnumCasePathLevelLinter(TestCaseListener tcl, NamingArguments namingArguments, EnumCollector enumCollector) {
        super(tcl, namingArguments, enumCollector);
    }

    @Override
    protected Map<SchemaWalker.SchemaLocation, List<String>> selectEnums(FuzzingData data) {
        return enumCollector.getEnums().entrySet().stream()
                .filter(e -> e.getKey().method() != null
                        && e.getKey().method().equalsIgnoreCase(data.getMethod().name())
                        && e.getKey().path().equalsIgnoreCase(data.getPath()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "Verifies enum-case consistency only for inline schemas of the current path/method.";
    }
}