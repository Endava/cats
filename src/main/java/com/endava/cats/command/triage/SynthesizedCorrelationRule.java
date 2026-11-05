package com.endava.cats.command.triage;

/**
 * A {@code --correlations} rule inferred from a {@code 400}/{@code 422} rejection of a request that was expected to succeed.
 *
 * @param path        the contract path the rule applies to
 * @param when        the rendered {@code when:} expression, or {@code null} for an unconditional rule
 * @param whenField   the trigger field ({@code null} for unconditional rules)
 * @param whenValue   the trigger value ({@code null} for unconditional and self-presence-guarded rules)
 * @param action      {@code REQUIRE} ({@code set:}), {@code REMOVE} ({@code omit:}) or {@code UNIQUE} (self-guarded {@code set:})
 * @param targetField the dotted field path to set or omit
 * @param value       the typed placeholder value for {@code set:} ({@code null} for {@code REMOVE})
 * @param sourceError the error signature the rule was inferred from
 */
public record SynthesizedCorrelationRule(String path,
                                         String when,
                                         String whenField,
                                         String whenValue,
                                         String action,
                                         String targetField,
                                         Object value,
                                         String sourceError) {
}
