package com.endava.cats.fuzzer.contract.base;

import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Base class for linters that check for unused elements in the OpenAPI components section.
 * It detects schemas, parameters, headers, or examples that are defined but not referenced anywhere in the contract.
 */
public abstract class AbstractUnusedElementsLinter extends BaseLinter {

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final CatsGlobalContext catsGlobalContext;

    protected AbstractUnusedElementsLinter(TestCaseListener tcl, CatsGlobalContext catsGlobalContext) {
        super(tcl);
        this.catsGlobalContext = catsGlobalContext;
    }

    /**
     * Returns the type of element being checked (e.g., "schemas", "parameters", "headers", "examples").
     *
     * @return the element type as a string.
     */
    protected abstract String getElementType();

    /**
     * Returns a function that retrieves the elements from the OpenAPI components.
     * This function should be implemented to return the appropriate map of elements based on the type.
     *
     * @return a function that takes Components and returns a map of elements.
     */
    protected abstract Function<Components, Map<String, ?>> getElementsFunction();

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Detect schemas defined under components.%s that are never referenced anywhere".formatted(this.getElementType()));
        testCaseListener.addExpectedResult(log, "All schemas defined in components.%s should be used at least once via $ref".formatted(this.getElementType()));

        OpenAPI openAPI = data.getOpenApi();
        Map<String, ?> schemas = openAPI.getComponents() != null ? getElementsFunction().apply(openAPI.getComponents()) : Map.of();
        if (MapUtils.isEmpty(schemas)) {
            testCaseListener.reportResultInfo(log, data, "No %s defined in components.%s"
                    .formatted(this.getElementType(), this.getElementType()));
            return;
        }

        Set<String> definedSchemas = schemas.keySet();
        Set<String> referencedSchemas = catsGlobalContext.getRefs();

        List<String> unused = definedSchemas.stream()
                .filter(CatsModelUtils::isNotGeneratedPattern)
                .filter(s -> referencedSchemas.stream().noneMatch(refSchema -> s.startsWith(refSchema + "_")))
                .filter(s -> !referencedSchemas.contains(s))
                .sorted()
                .toList();

        super.addDefaultsForPathAgnosticFuzzers();

        if (!unused.isEmpty()) {
            testCaseListener.reportResultWarn(
                    log,
                    data,
                    "Unused component %s found".formatted(this.getElementType()),
                    unused.stream().map("- %s"::formatted).collect(java.util.stream.Collectors.joining("\n"))
            );
        } else {
            testCaseListener.reportResultInfo(log, data, "All %s in components.%s are used at least once".formatted(this.getElementType(), this.getElementType()));
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "unused-%s-linter".formatted(this.getElementType());
    }

    @Override
    public String description() {
        return "flags any component %s defined under components.%s that are not referenced via $ref anywhere in the contract"
                .formatted(this.getElementType(), this.getElementType());
    }
}
