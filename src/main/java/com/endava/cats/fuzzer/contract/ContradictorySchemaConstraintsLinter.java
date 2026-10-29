package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Detects contradictory or invalid schema constraints such as minLength &gt; maxLength,
 * minimum &gt; maximum, minItems &gt; maxItems, minProperties &gt; maxProperties,
 * invalid regex patterns, undeclared required properties, and out-of-enum defaults/examples.
 */
@Linter
@Singleton
public class ContradictorySchemaConstraintsLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new ContradictorySchemaConstraintsLinter instance.
     *
     * @param tcl the test case listener
     */
    public ContradictorySchemaConstraintsLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check all schemas for contradictory bounds, invalid regex patterns, undeclared required fields, and invalid defaults/examples");
        testCaseListener.addExpectedResult(log, "All schemas must have consistent bounds (min <= max), valid regex patterns, declared required properties, and valid defaults/examples");
        super.addDefaultsForPathAgnosticFuzzers();

        if (data.getOpenApi() == null) {
            testCaseListener.skipTest(log, "OpenAPI specification not available");
            return;
        }

        List<String> violations = new ArrayList<>();
        SchemaWalker.walk(data.getOpenApi(), (loc, schema) -> validateSchema(loc, schema, violations));

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All schemas have consistent and valid constraints");
        } else {
            testCaseListener.reportResultError(log, data, "Contradictory or broken schema constraints found", String.join("\n", violations));
        }
    }

    static void validateSchema(SchemaLocation loc, Schema<?> schema, List<String> violations) {
        if (schema == null) {
            return;
        }
        String location = loc.fqn();
        checkLengthBounds(location, schema, violations);
        checkNumericBounds(location, schema, violations);
        checkArrayAndPropertyBounds(location, schema, violations);
        checkRegexPattern(location, schema, violations);
        checkRequiredProperties(loc, schema, violations);
        checkEnumDefaultAndExample(location, schema, violations);
    }

    private static void checkLengthBounds(String location, Schema<?> schema, List<String> violations) {
        Integer minLen = schema.getMinLength();
        Integer maxLen = schema.getMaxLength();
        if (minLen != null && minLen < 0) {
            violations.add("Schema '%s' has negative minLength (%d)".formatted(location, minLen));
        }
        if (maxLen != null && maxLen < 0) {
            violations.add("Schema '%s' has negative maxLength (%d)".formatted(location, maxLen));
        }
        if (minLen != null && maxLen != null && minLen > maxLen) {
            violations.add("Schema '%s' has minLength (%d) greater than maxLength (%d)".formatted(location, minLen, maxLen));
        }
    }

    private static void checkNumericBounds(String location, Schema<?> schema, List<String> violations) {
        if (schema.getMinimum() != null && schema.getMaximum() != null
                && schema.getMinimum().compareTo(schema.getMaximum()) > 0) {
            violations.add("Schema '%s' has minimum (%s) greater than maximum (%s)"
                    .formatted(location, schema.getMinimum(), schema.getMaximum()));
        }
    }

    private static void checkArrayAndPropertyBounds(String location, Schema<?> schema, List<String> violations) {
        if (schema.getMinItems() != null && schema.getMaxItems() != null && schema.getMinItems() > schema.getMaxItems()) {
            violations.add("Schema '%s' has minItems (%d) greater than maxItems (%d)"
                    .formatted(location, schema.getMinItems(), schema.getMaxItems()));
        }
        if (schema.getMinProperties() != null && schema.getMaxProperties() != null
                && schema.getMinProperties() > schema.getMaxProperties()) {
            violations.add("Schema '%s' has minProperties (%d) greater than maxProperties (%d)"
                    .formatted(location, schema.getMinProperties(), schema.getMaxProperties()));
        }
    }

    private static void checkRegexPattern(String location, Schema<?> schema, List<String> violations) {
        if (StringUtils.isNotBlank(schema.getPattern())) {
            try {
                Pattern.compile(schema.getPattern());
            } catch (PatternSyntaxException _) {
                violations.add("Schema '%s' defines an invalid regex pattern '%s'".formatted(location, schema.getPattern()));
            }
        }
    }

    private static void checkRequiredProperties(SchemaLocation loc, Schema<?> schema, List<String> violations) {
        if (CatsUtil.isEmpty(schema.getRequired()) || CatsModelUtils.isComposedSchema(schema)
                || CatsUtil.isEmpty(schema.getProperties())) {
            return;
        }
        String pointer = loc.pointer();
        if (pointer != null && (pointer.contains("/allOf/") || pointer.contains("/oneOf/") || pointer.contains("/anyOf/"))) {
            return;
        }
        String location = loc.fqn();
        Set<String> definedProps = schema.getProperties().keySet();
        for (String reqProp : schema.getRequired()) {
            if (!definedProps.contains(reqProp)) {
                violations.add("Schema '%s' marks property '%s' as required, but it is not defined in properties"
                        .formatted(location, reqProp));
            }
        }
    }

    private static void checkEnumDefaultAndExample(String location, Schema<?> schema, List<String> violations) {
        if (CatsUtil.isEmpty(schema.getEnum())) {
            return;
        }
        Set<String> allowedValues = schema.getEnum().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
        if (schema.getDefault() != null && !allowedValues.contains(schema.getDefault().toString())) {
            violations.add("Schema '%s' has default value '%s' not present in enum %s"
                    .formatted(location, schema.getDefault(), schema.getEnum()));
        }
        if (schema.getExample() != null && !allowedValues.contains(schema.getExample().toString())) {
            violations.add("Schema '%s' has example value '%s' not present in enum %s"
                    .formatted(location, schema.getExample(), schema.getEnum()));
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "contradictory-schema-constraints-linter";
    }

    @Override
    public String description() {
        return "detects contradictory or invalid schema constraints (min > max bounds, invalid regex patterns, undeclared required fields, and invalid enum defaults/examples)";
    }
}
