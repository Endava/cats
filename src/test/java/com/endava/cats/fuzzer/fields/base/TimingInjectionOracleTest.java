package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.SecurityFinding;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@QuarkusTest
class TimingInjectionOracleTest {

    static Stream<Arguments> supportedSleepPayloads() {
        return Stream.of(
                Arguments.of("'; SELECT SLEEP(5)--", 5, "'; SELECT SLEEP(1)--"),
                Arguments.of("'; SELECT pg_sleep(5)--", 5, "'; SELECT pg_sleep(1)--"),
                Arguments.of("' AND SLEEP(5)--", 5, "' AND SLEEP(1)--"),
                Arguments.of("'; WAITFOR DELAY '0:0:5'--", 5, "'; WAITFOR DELAY '0:0:1'--"),
                Arguments.of("; sleep 5", 5, "; sleep 1"),
                Arguments.of("$(sleep 5)", 5, "$(sleep 1)"),
                Arguments.of("'; sleep(5000); var foo='", 5, "'; sleep(1000); var foo='"),
                Arguments.of("{\"$where\": \"sleep(5000)\"}", 5, "{\"$where\": \"sleep(1000)\"}")
        );
    }

    static Stream<String> unsupportedOrSubThresholdPayloads() {
        return Stream.of(
                "' OR '1'='1",
                "; ls -la",
                "; ping -c 3 127.0.0.1",
                "; sleep 2",
                "'; sleep(1000); var foo='",
                "",
                null
        );
    }

    @ParameterizedTest
    @MethodSource("supportedSleepPayloads")
    void shouldParseSupportedSleepPayloadsAndRewriteDelay(String payload, int expectedHighSeconds, String expectedOneSecPayload) {
        Optional<TimingInjectionOracle.TimingSpec> spec = TimingInjectionOracle.parseTimingSpec(payload);

        Assertions.assertThat(spec).isPresent();
        Assertions.assertThat(spec.orElseThrow().highDelaySeconds()).isEqualTo(expectedHighSeconds);
        Assertions.assertThat(spec.orElseThrow().payloadBuilder().apply(1)).isEqualTo(expectedOneSecPayload);
    }

    @ParameterizedTest
    @MethodSource("unsupportedOrSubThresholdPayloads")
    void shouldReturnEmptyTimingSpecForNonTimingOrSubThresholdPayloads(String payload) {
        Assertions.assertThat(TimingInjectionOracle.parseTimingSpec(payload)).isEmpty();
    }

    @Test
    void shouldSkipVerificationWithoutSendingExtraProbesWhenInitialResponseIsFast() {
        AtomicInteger baselineCalls = new AtomicInteger();
        AtomicInteger probeCalls = new AtomicInteger();
        TimingInjectionOracle.TimingSpec spec = TimingInjectionOracle.parseTimingSpec("; sleep 5").orElseThrow();

        Optional<SecurityFinding> finding = TimingInjectionOracle.verify(
                response(200, 45L),
                spec,
                new TimingInjectionOracle.TimingContext("Command injection vulnerability detected", "Command",
                        () -> {
                            baselineCalls.incrementAndGet();
                            return 20L;
                        },
                        _ -> {
                            probeCalls.incrementAndGet();
                            return response(200, 1_020L);
                        }));

        Assertions.assertThat(finding).isEmpty();
        Assertions.assertThat(baselineCalls).hasValue(0);
        Assertions.assertThat(probeCalls).hasValue(0);
    }

    @Test
    void shouldSkipVerificationWhenBaselineExceedsTwoSeconds() {
        AtomicInteger probeCalls = new AtomicInteger();
        TimingInjectionOracle.TimingSpec spec = TimingInjectionOracle.parseTimingSpec("'; SELECT SLEEP(5)--").orElseThrow();

        Optional<SecurityFinding> finding = TimingInjectionOracle.verify(
                response(200, 5_100L),
                spec,
                new TimingInjectionOracle.TimingContext("SQL injection vulnerability detected", "SQL",
                        () -> 2_150L,
                        _ -> {
                            probeCalls.incrementAndGet();
                            return response(200, 1_050L);
                        }));

        Assertions.assertThat(finding).isEmpty();
        Assertions.assertThat(probeCalls).hasValue(0);
    }

    @Test
    void shouldRejectFixedUpstreamTimeoutWhereLowDelayProbeStillTakesFiveSeconds() {
        AtomicInteger probeCalls = new AtomicInteger();
        TimingInjectionOracle.TimingSpec spec = TimingInjectionOracle.parseTimingSpec("'; SELECT SLEEP(5)--").orElseThrow();

        Optional<SecurityFinding> finding = TimingInjectionOracle.verify(
                response(200, 5_050L),
                spec,
                new TimingInjectionOracle.TimingContext("SQL injection vulnerability detected", "SQL",
                        () -> 40L,
                        _ -> {
                            probeCalls.incrementAndGet();
                            return response(200, 5_040L);
                        }));

        Assertions.assertThat(finding).isEmpty();
        Assertions.assertThat(probeCalls).hasValue(1);
    }

    @Test
    void shouldRejectClientTimeout9xxResponseCodes() {
        TimingInjectionOracle.TimingSpec spec = TimingInjectionOracle.parseTimingSpec("; sleep 5").orElseThrow();

        Optional<SecurityFinding> finding = TimingInjectionOracle.verify(
                response(954, 10_010L),
                spec,
                new TimingInjectionOracle.TimingContext("Command injection vulnerability detected", "Command",
                        () -> 30L,
                        seconds -> response(200, seconds * 1_000L + 30L)));

        Assertions.assertThat(finding).isEmpty();
    }

    @Test
    void shouldConfirmFindingWhenBaselineIsFastAndAlternatingProbesCorrelateLinearly() {
        AtomicInteger probeCalls = new AtomicInteger();
        TimingInjectionOracle.TimingSpec spec = TimingInjectionOracle.parseTimingSpec("'; sleep(5000); var foo='").orElseThrow();

        Optional<SecurityFinding> finding = TimingInjectionOracle.verify(
                response(200, 5_040L),
                spec,
                new TimingInjectionOracle.TimingContext("NoSQL injection vulnerability detected", "NoSQL",
                        () -> 35L,
                        seconds -> {
                            probeCalls.incrementAndGet();
                            return response(200, seconds * 1_000L + 35L);
                        }));

        Assertions.assertThat(finding).isPresent();
        Assertions.assertThat(finding.orElseThrow().confidence()).isEqualTo(FindingConfidence.CONFIRMED);
        Assertions.assertThat(finding.orElseThrow().reason()).isEqualTo("NoSQL injection vulnerability detected");
        Assertions.assertThat(finding.orElseThrow().message()).contains("r^2=1.00", "slope=1.00");
        Assertions.assertThat(probeCalls).hasValue(3);
    }

    private static CatsResponse response(int status, long elapsedMs) {
        return CatsResponse.builder().responseCode(status).responseTimeInMs(elapsedMs).body("{}").build();
    }
}
