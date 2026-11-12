package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.SecurityFinding;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@QuarkusTest
class BooleanDifferentialOracleTest {
    private static final CatsResponse MATCHING_USER = response(200, "{\"users\":[{\"id\":1,\"username\":\"alice\"}]}");
    private static final CatsResponse EMPTY_USERS = response(200, "{\"users\":[]}");
    private static final BooleanDifferentialOracle.FindingMeta SQL_META =
            new BooleanDifferentialOracle.FindingMeta("SQL injection vulnerability detected", "SQL");

    @Test
    void shouldConfirmAndStyleBooleanDifferentialWhenTrueMatchesBaselineFalseDiffersAndPairReproduces() {
        BooleanInjectionPair pair = BooleanInjectionPair.andSuffix("SQL single-quote AND", "' AND '1'='1", "' AND '1'='2");
        String neutral = pair.neutralPayload().apply("alice");
        String divergent = pair.divergentPayload().apply("alice");

        Optional<SecurityFinding> finding = BooleanDifferentialOracle.verify(
                EMPTY_USERS,
                new BooleanDifferentialOracle.DifferentialCheck(
                        pair,
                        new BooleanDifferentialOracle.ProbeValues("alice", neutral, divergent),
                        new BooleanDifferentialOracle.ProbeTransport(
                                () -> MATCHING_USER,
                                () -> MATCHING_USER,
                                payload -> payload.equals(neutral) ? MATCHING_USER : EMPTY_USERS),
                        SQL_META));

        Assertions.assertThat(finding).isPresent();
        Assertions.assertThat(finding.orElseThrow().confidence()).isEqualTo(FindingConfidence.CONFIRMED);
        Assertions.assertThat(finding.orElseThrow().reason()).isEqualTo("SQL injection vulnerability detected");
    }

    @Test
    void shouldConfirmOrStyleBooleanDifferentialWhenBaselineIsEmptyAndOrTrueBroadensResults() {
        BooleanInjectionPair pair = BooleanInjectionPair.orSuffix("SQL single-quote OR", "' OR '1'='2", "' OR '1'='1");
        String neutral = pair.neutralPayload().apply("unknown");
        String divergent = pair.divergentPayload().apply("unknown");

        Optional<SecurityFinding> finding = BooleanDifferentialOracle.verify(
                MATCHING_USER,
                new BooleanDifferentialOracle.DifferentialCheck(
                        pair,
                        new BooleanDifferentialOracle.ProbeValues("unknown", neutral, divergent),
                        new BooleanDifferentialOracle.ProbeTransport(
                                () -> EMPTY_USERS,
                                () -> EMPTY_USERS,
                                payload -> payload.equals(neutral) ? EMPTY_USERS : MATCHING_USER),
                        SQL_META));

        Assertions.assertThat(finding).isPresent();
        Assertions.assertThat(finding.orElseThrow().confidence()).isEqualTo(FindingConfidence.CONFIRMED);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422, 500, 954})
    void shouldRejectValidationAndServerErrorResponsesWithoutFetchingBaselineOrNeutralProbe(int statusCode) {
        AtomicInteger baselineCalls = new AtomicInteger();
        AtomicInteger replayCalls = new AtomicInteger();
        BooleanInjectionPair pair = BooleanInjectionPair.andSuffix("SQL single-quote AND", "' AND '1'='1", "' AND '1'='2");

        Optional<SecurityFinding> finding = BooleanDifferentialOracle.verify(
                response(statusCode, "{\"error\":\"rejected\"}"),
                new BooleanDifferentialOracle.DifferentialCheck(
                        pair,
                        new BooleanDifferentialOracle.ProbeValues("alice", "alice' AND '1'='1", "alice' AND '1'='2"),
                        new BooleanDifferentialOracle.ProbeTransport(
                                () -> {
                                    baselineCalls.incrementAndGet();
                                    return MATCHING_USER;
                                },
                                () -> MATCHING_USER,
                                _ -> {
                                    replayCalls.incrementAndGet();
                                    return MATCHING_USER;
                                }),
                        SQL_META));

        Assertions.assertThat(finding).isEmpty();
        Assertions.assertThat(baselineCalls).hasValue(0);
        Assertions.assertThat(replayCalls).hasValue(0);
    }

    @Test
    void shouldSkipNeutralProbeWhenFieldIsIgnoredAndDivergentMatchesBaseline() {
        AtomicInteger replayCalls = new AtomicInteger();
        BooleanInjectionPair pair = BooleanInjectionPair.andSuffix("SQL single-quote AND", "' AND '1'='1", "' AND '1'='2");

        Optional<SecurityFinding> finding = BooleanDifferentialOracle.verify(
                MATCHING_USER,
                new BooleanDifferentialOracle.DifferentialCheck(
                        pair,
                        new BooleanDifferentialOracle.ProbeValues("alice", "alice' AND '1'='1", "alice' AND '1'='2"),
                        new BooleanDifferentialOracle.ProbeTransport(
                                () -> MATCHING_USER,
                                () -> MATCHING_USER,
                                _ -> {
                                    replayCalls.incrementAndGet();
                                    return MATCHING_USER;
                                }),
                        SQL_META));

        Assertions.assertThat(finding).isEmpty();
        Assertions.assertThat(replayCalls).hasValue(0);
    }

    @Test
    void shouldRejectWhenBaselineIsUnstableAcrossReads() {
        BooleanInjectionPair pair = BooleanInjectionPair.andSuffix("SQL single-quote AND", "' AND '1'='1", "' AND '1'='2");
        String neutral = pair.neutralPayload().apply("alice");
        String divergent = pair.divergentPayload().apply("alice");

        Optional<SecurityFinding> finding = BooleanDifferentialOracle.verify(
                EMPTY_USERS,
                new BooleanDifferentialOracle.DifferentialCheck(
                        pair,
                        new BooleanDifferentialOracle.ProbeValues("alice", neutral, divergent),
                        new BooleanDifferentialOracle.ProbeTransport(
                                () -> MATCHING_USER,
                                () -> EMPTY_USERS,
                                payload -> payload.equals(neutral) ? MATCHING_USER : EMPTY_USERS),
                        SQL_META));

        Assertions.assertThat(finding).isEmpty();
    }

    @Test
    void shouldRejectWhenDifferentialDoesNotReproduceOnReplay() {
        BooleanInjectionPair pair = BooleanInjectionPair.andSuffix("SQL single-quote AND", "' AND '1'='1", "' AND '1'='2");
        String neutral = pair.neutralPayload().apply("alice");
        String divergent = pair.divergentPayload().apply("alice");

        Optional<SecurityFinding> finding = BooleanDifferentialOracle.verify(
                EMPTY_USERS,
                new BooleanDifferentialOracle.DifferentialCheck(
                        pair,
                        new BooleanDifferentialOracle.ProbeValues("alice", neutral, divergent),
                        new BooleanDifferentialOracle.ProbeTransport(
                                () -> MATCHING_USER,
                                () -> MATCHING_USER,
                                _ -> MATCHING_USER),
                        SQL_META));

        Assertions.assertThat(finding).isEmpty();
    }

    private static CatsResponse response(int status, String body) {
        return CatsResponse.builder().responseCode(status).body(body).build();
    }
}
