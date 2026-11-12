package com.endava.cats.fuzzer.fields.base;

import java.util.function.UnaryOperator;

/**
 * A paired boolean-blind injection probe consisting of a baseline-preserving (neutral) payload and a
 * query-altering (divergent) payload.
 *
 * @param label               concise description of the boolean pair
 * @param neutralPayload      builds the payload expected to preserve the fresh baseline response
 * @param divergentPayload    builds the payload expected to alter the response relative to the fresh baseline
 * @param require2xxDivergent true when the divergent probe broadens results (e.g. {@code OR 1=1}) and must return 2XX
 */
public record BooleanInjectionPair(String label, UnaryOperator<String> neutralPayload,
                                   UnaryOperator<String> divergentPayload, boolean require2xxDivergent) {

    /**
     * Builds an {@code AND}-style pair appended to the original field value: {@code orig + trueSuffix} preserves
     * matching rows while {@code orig + falseSuffix} restricts them.
     */
    public static BooleanInjectionPair andSuffix(String label, String trueSuffix, String falseSuffix) {
        return new BooleanInjectionPair(label, orig -> orig + trueSuffix, orig -> orig + falseSuffix, false);
    }

    /**
     * Builds an {@code OR}-style pair appended to the original field value: {@code orig + falseSuffix} preserves
     * zero-row baselines while {@code orig + trueSuffix} broadens results and must return {@code 2XX}.
     */
    public static BooleanInjectionPair orSuffix(String label, String falseSuffix, String trueSuffix) {
        return new BooleanInjectionPair(label, orig -> orig + falseSuffix, orig -> orig + trueSuffix, true);
    }

    /**
     * Builds a whole-value replacement pair where {@code neutralValue} preserves empty baselines and
     * {@code divergentValue} broadens results with a {@code 2XX} response.
     */
    public static BooleanInjectionPair replaceBroadening(String label, String neutralValue, String divergentValue) {
        return new BooleanInjectionPair(label, _ -> neutralValue, _ -> divergentValue, true);
    }
}
