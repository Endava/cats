package com.endava.cats.fuzzer.contract.base;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.util.List;

/**
 * Abstract base class for schema linters that validate schemas against specific rules.
 * It collects schemas, filters them based on a predicate, and validates them using a provided rule.
 *
 * @param <E> the type of schema being validated
 */
public abstract class AbstractSchemaLinter<E> extends BaseLinter {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(AbstractSchemaLinter.class);

    @Getter
    protected SchemaLinterContext<E> context;

    protected AbstractSchemaLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public final void process(FuzzingData data) {
        testCaseListener.addScenario(logger, context.scenarioMessage());
        testCaseListener.addExpectedResult(logger, context.expectedResult());

        if (isGlobalFuzzer(data)) {
            addDefaultsForPathAgnosticFuzzers();
        }

        if (MapUtils.isEmpty(context.collector().get())) {
            testCaseListener.skipTest(logger, "No data found to validate for %s".formatted(runKey(data)));
            return;
        }

        List<String> bad = context.collector().get().entrySet()
                .stream()
                .filter(e -> context.filter().test(e.getKey(), data) && !context.validator().test(e.getValue()))
                .map(e -> context.format().apply(e.getKey(), e.getValue()))
                .toList();

        if (bad.isEmpty()) {
            testCaseListener.reportResultInfo(logger, data, context.successMessage());
        } else {
            testCaseListener.reportResultWarn(logger, data, context.failureMessage(), String.join("\n", bad));
        }
    }

    private boolean isGlobalFuzzer(FuzzingData data) {
        return !context.runKeyProvider().apply(data).contains(data.getMethod().name());
    }

    @Override
    protected String runKey(FuzzingData data) {
        return context.runKeyProvider().apply(data);
    }
}
