package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates polymorphism discriminator definitions on oneOf/anyOf/allOf schemas.
 */
@Linter
@Singleton
public class PolymorphismDiscriminatorLinter extends BaseLinter {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PolymorphismDiscriminatorLinter instance.
     *
     * @param tcl the test case listener
     */
    public PolymorphismDiscriminatorLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that polymorphism discriminator definitions have a valid propertyName, valid mapping targets, and property presence in sub-schemas");
        testCaseListener.addExpectedResult(log, "All discriminators must define a non-blank propertyName present in sub-schemas and valid mapping references");
        super.addDefaultsForPathAgnosticFuzzers();

        if (data.getOpenApi() == null) {
            testCaseListener.skipTest(log, "OpenAPI specification not available");
            return;
        }

        Map<String, Schema> componentSchemas = OpenApiUtils.getComponentSchemas(data.getOpenApi());

        List<String> violations = new ArrayList<>();
        SchemaWalker.walk(data.getOpenApi(), (loc, schema) -> validateDiscriminator(loc, schema, componentSchemas, violations));

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All polymorphism discriminators are properly defined");
        } else {
            testCaseListener.reportResultWarn(log, data, "Polymorphism discriminator issues detected", String.join("\n", violations));
        }
    }

    @SuppressWarnings("rawtypes")
    private static void validateDiscriminator(SchemaLocation loc, Schema<?> schema,
                                              Map<String, Schema> componentSchemas, List<String> violations) {
        if (schema == null || schema.getDiscriminator() == null) {
            return;
        }
        Discriminator discriminator = schema.getDiscriminator();
        String location = loc.fqn();
        String propName = discriminator.getPropertyName();

        if (StringUtils.isBlank(propName)) {
            violations.add("Schema '%s' defines a discriminator with a blank propertyName".formatted(location));
            return;
        }

        if (discriminator.getMapping() != null) {
            for (Map.Entry<String, String> entry : discriminator.getMapping().entrySet()) {
                String targetName = CatsModelUtils.getSimpleRef(entry.getValue());
                if (targetName == null || !componentSchemas.containsKey(targetName)) {
                    violations.add("Schema '%s' discriminator mapping '%s' -> '%s' does not resolve to an existing component schema"
                            .formatted(location, entry.getKey(), entry.getValue()));
                }
            }
        }

        boolean definedOnParent = schema.getProperties() != null && schema.getProperties().containsKey(propName);
        if (definedOnParent) {
            return;
        }

        List<Schema> variants = new ArrayList<>();
        if (CatsUtil.isNotEmpty(schema.getOneOf())) {
            variants.addAll(schema.getOneOf());
        }
        if (CatsUtil.isNotEmpty(schema.getAnyOf())) {
            variants.addAll(schema.getAnyOf());
        }

        for (Schema<?> variant : variants) {
            Schema<?> resolved = CatsModelUtils.resolveSchema(variant, componentSchemas);
            if (resolved != null && !declaresProperty(resolved, propName, componentSchemas, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                String variantName = variant.get$ref() != null ? CatsModelUtils.getSimpleRef(variant.get$ref()) : "inline variant";
                violations.add("Sub-schema '%s' in '%s' does not define discriminator property '%s'"
                        .formatted(variantName, location, propName));
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean declaresProperty(Schema<?> schema, String propName,
                                            Map<String, Schema> componentSchemas, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return false;
        }
        if (schema.getProperties() != null && schema.getProperties().containsKey(propName)) {
            return true;
        }
        if (schema.getRequired() != null && schema.getRequired().contains(propName)) {
            return true;
        }
        for (Schema<?> subSchema : CatsModelUtils.getComposedSchemas(schema)) {
            Schema<?> resolved = CatsModelUtils.resolveSchema(subSchema, componentSchemas);
            if (declaresProperty(resolved, propName, componentSchemas, visited)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "polymorphism-discriminator-linter";
    }

    @Override
    public String description() {
        return "validates that oneOf/anyOf discriminator definitions specify a non-blank propertyName present in sub-schemas and valid mapping references";
    }
}
