package com.endava.cats.fuzzer.contract.base;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.EnumCollector;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Template-method base class that holds the common algorithm for
 * enum-case validation; subclasses decide which enums to scan.
 */
public abstract class AbstractEnumCaseLinter extends BaseLinter {
    protected final PrettyLogger log = PrettyLoggerFactory.getLogger(getClass());
    protected final NamingArguments namingArguments;
    protected final EnumCollector enumCollector;

    /**
     * Constructor for the enum case linter.
     *
     * @param tcl             the test case listener to report results
     * @param namingArguments the naming arguments containing the regex pattern for enum validation
     * @param enumCollector   the collector that gathers enums from the schema
     */
    protected AbstractEnumCaseLinter(TestCaseListener tcl, NamingArguments namingArguments, EnumCollector enumCollector) {
        super(tcl);
        this.namingArguments = namingArguments;
        this.enumCollector = enumCollector;
    }

    @Override
    public final void process(FuzzingData data) {
        Pattern allowedPattern = namingArguments.getEnumsNaming().getPattern();
        testCaseListener.addScenario(log,
                "Validate that every string enum value complies with the configured case format (%s)"
                        .formatted(allowedPattern.pattern()));
        testCaseListener.addExpectedResult(log,
                "All enum values should match the pattern %s".formatted(allowedPattern));
        Map<SchemaLocation, List<String>> enums = selectEnums(data);
        if (enums.isEmpty()) {
            testCaseListener.skipTest(log, "No enums found to validate for %s".formatted(runKey(data)));
            return;
        }

        List<String> violations = new ArrayList<>();

        enums.forEach((loc, values) -> {
            boolean broken = values.stream()
                    .anyMatch(v -> !allowedPattern.matcher(v).matches());
            if (broken) {
                violations.add("Schema at location '%s' contains enum value(s) %s that violate naming convention"
                        .formatted(loc.fqn(), values));
            }
        });

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data,
                    "All enum values respect the allowed case style");
        } else {
            testCaseListener.reportResultWarn(log, data,
                    "Enum case mismatches detected",
                    String.join("\n", violations));
        }
    }

    /**
     * Subclasses provide the subset of enums they want to check.
     */
    protected abstract Map<SchemaLocation, List<String>> selectEnums(FuzzingData data);
}