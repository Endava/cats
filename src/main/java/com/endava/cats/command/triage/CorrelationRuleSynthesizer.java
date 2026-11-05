package com.endava.cats.command.triage;

import java.util.Optional;

/**
 * Turns a {@link FailureAnalysis} into a {@code --correlations} rule:
 * <ul>
 *     <li>{@code REQUIRE} / {@code REMOVE}: {@code when: "<trigger> == '<value>'"} with a typed {@code set:} placeholder or
 *     an {@code omit:}; unconditional when no trustworthy trigger exists</li>
 *     <li>{@code UNIQUE}: a self-presence guard ({@code when: "name is not null"}) with a unique-value generator, which
 *     populates the field without marking it as required</li>
 * </ul>
 * No rule is produced for HTML error pages or when the server rejected a value the fuzzer is mutating: correlations
 * cannot override such fields, only {@code --refData} pinning can.
 */
final class CorrelationRuleSynthesizer {
    private final TriggerResolver triggerResolver;
    private final PlaceholderValues placeholderValues;

    CorrelationRuleSynthesizer(RequestSchemaLookup schemaLookup) {
        this.triggerResolver = new TriggerResolver(schemaLookup);
        this.placeholderValues = new PlaceholderValues(schemaLookup);
    }

    Optional<SynthesizedCorrelationRule> synthesize(TriageTestCase testCase, FailureAnalysis analysis) {
        if (analysis.message().html() || analysis.isDomainRejectionOfMutatedField()) {
            return Optional.empty();
        }
        return analysis.intent().flatMap(intent -> analysis.referencedField()
                .flatMap(field -> ruleFor(intent, testCase, analysis, field)));
    }

    private Optional<SynthesizedCorrelationRule> ruleFor(FailureIntent intent, TriageTestCase testCase, FailureAnalysis analysis, String field) {
        return switch (intent) {
            case UNIQUE -> uniquenessRule(testCase, analysis, field);
            case REMOVE -> Optional.of(conditionalRule(testCase, analysis, field, RuleShape.omit()));
            case REQUIRE -> Optional.of(conditionalRule(testCase, analysis, field, RuleShape.set(requiredValue(testCase, analysis, field))));
        };
    }

    private Object requiredValue(TriageTestCase testCase, FailureAnalysis analysis, String field) {
        return placeholderValues.forRequirement(testCase, field, analysis.message().lower(),
                PayloadFields.valueAt(analysis.payload(), field));
    }

    private Optional<SynthesizedCorrelationRule> uniquenessRule(TriageTestCase testCase, FailureAnalysis analysis, String field) {
        return placeholderValues.forUniqueness(testCase, field)
                .map(value -> new SynthesizedCorrelationRule(testCase.path(), field + " is not null", field, null,
                        FailureIntent.UNIQUE.name(), field, value, analysis.message().signature()));
    }

    private SynthesizedCorrelationRule conditionalRule(TriageTestCase testCase, FailureAnalysis analysis, String field, RuleShape shape) {
        Optional<TriggerResolver.Trigger> trigger = triggerResolver.resolve(testCase, analysis.payload(), analysis.message().clean(), field);
        return new SynthesizedCorrelationRule(testCase.path(),
                trigger.map(TriggerResolver.Trigger::expression).orElse(null),
                trigger.map(TriggerResolver.Trigger::field).orElse(null),
                trigger.map(TriggerResolver.Trigger::value).orElse(null),
                shape.action().name(), field, shape.value(), analysis.message().signature());
    }

    private record RuleShape(FailureIntent action, Object value) {
        static RuleShape omit() {
            return new RuleShape(FailureIntent.REMOVE, null);
        }

        static RuleShape set(Object value) {
            return new RuleShape(FailureIntent.REQUIRE, value);
        }
    }
}
