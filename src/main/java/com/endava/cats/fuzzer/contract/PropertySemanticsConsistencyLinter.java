package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.api.SchemaWalker;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Verifies semantic property consistency across schemas: ensures audit timestamp fields
 * use a consistent type/format across the contract and flags floating-point monetary fields.
 */
@Linter
@Singleton
public class PropertySemanticsConsistencyLinter extends BaseLinter {
    private static final Set<String> MONETARY_FIELD_NAMES = Set.of(
            "amount", "price", "fee", "balance", "totalamount", "unitprice", "subtotal", "taxamount"
    );

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new PropertySemanticsConsistencyLinter instance.
     *
     * @param tcl the test case listener
     */
    public PropertySemanticsConsistencyLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check that audit timestamp properties share a consistent type/format and monetary properties avoid floating-point types");
        testCaseListener.addExpectedResult(log, "Audit timestamp fields should use a consistent type/format across schemas, and monetary fields should avoid binary floating-point number types");
        super.addDefaultsForPathAgnosticFuzzers();

        if (data.getOpenApi() == null) {
            testCaseListener.skipTest(log, "OpenAPI specification not available");
            return;
        }

        List<String> violations = new ArrayList<>();
        Map<String, List<String>> timestampFormats = new LinkedHashMap<>();

        SchemaWalker.walk(data.getOpenApi(), (loc, schema) -> inspectSchemaProperties(loc, schema, timestampFormats, violations));

        if (timestampFormats.size() > 1) {
            StringBuilder drift = new StringBuilder("Audit timestamp properties use inconsistent types/formats across schemas: ");
            timestampFormats.forEach((sig, locations) ->
                    drift.append("[").append(sig).append("] at ").append(locations).append("; "));
            violations.add(drift.toString().stripTrailing());
        }

        if (violations.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "All semantic properties (timestamps and monetary fields) use consistent, safe types");
        } else {
            testCaseListener.reportResultWarn(log, data, "Property semantics consistency issues detected", String.join("\n", violations));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void inspectSchemaProperties(SchemaLocation loc, Schema<?> schema,
                                                Map<String, List<String>> timestampFormats,
                                                List<String> violations) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        Set<String> seenProps = new LinkedHashSet<>();
        for (Map.Entry<String, Schema> entry : ((Map<String, Schema>) schema.getProperties()).entrySet()) {
            String propName = entry.getKey();
            Schema<?> propSchema = entry.getValue();
            if (propSchema == null || !seenProps.add(propName)) {
                continue;
            }
            String propLocation = loc.fqn() + "." + propName;
            String normalized = WordUtils.normalizeAlphaNumeric(propName);

            if (MONETARY_FIELD_NAMES.contains(normalized) && isFloatingPointMonetarySchema(propSchema)) {
                violations.add("Monetary property '%s' uses floating-point type '%s'%s; prefer integer minor units or string decimal"
                        .formatted(propLocation, propSchema.getType(),
                                StringUtils.isNotBlank(propSchema.getFormat()) ? " (" + propSchema.getFormat() + ")" : ""));
            }

            if (WordUtils.isAuditTimestampField(propName) && StringUtils.isNotBlank(propSchema.getType())) {
                String sig = propSchema.getType().toLowerCase(Locale.ROOT)
                        + (StringUtils.isNotBlank(propSchema.getFormat()) ? ":" + propSchema.getFormat().toLowerCase(Locale.ROOT) : "");
                timestampFormats.computeIfAbsent(sig, _ -> new ArrayList<>()).add(propLocation);
            }
        }
    }

    private static boolean isFloatingPointMonetarySchema(Schema<?> propSchema) {
        if (!CatsModelUtils.isNumberSchema(propSchema) || CatsModelUtils.isIntegerSchema(propSchema)) {
            return false;
        }
        String format = propSchema.getFormat();
        return format == null || "float".equalsIgnoreCase(format) || "double".equalsIgnoreCase(format);
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "property-semantics-consistency-linter";
    }

    @Override
    public String description() {
        return "verifies that audit timestamp properties share a consistent type/format across schemas and flags floating-point monetary fields";
    }
}
