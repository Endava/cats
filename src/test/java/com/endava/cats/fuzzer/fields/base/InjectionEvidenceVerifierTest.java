package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

@QuarkusTest
class InjectionEvidenceVerifierTest {
    private static final String EVIDENCE = "root:x:0:0:root:/root:/bin/bash";
    private static final Predicate<CatsResponse> DETECTOR = response -> response.getBody().contains(EVIDENCE);

    private RuntimeResourcePool runtimeResourcePool;
    private InjectionEvidenceVerifier verifier;
    private FuzzingData data;
    private AtomicInteger replays;

    @BeforeEach
    void setup() {
        runtimeResourcePool = Mockito.mock(RuntimeResourcePool.class);
        verifier = new InjectionEvidenceVerifier(runtimeResourcePool);
        data = FuzzingData.builder().path("/files").method(HttpMethod.POST).build();
        replays = new AtomicInteger();
    }

    @ParameterizedTest
    @CsvSource({
            "true, NOVEL, CONFIRMED",
            "true, ABSENT, FIRM",
            "false, NOVEL, FIRM",
            "false, ABSENT, TENTATIVE"
    })
    void shouldComputeHighestConfidenceFromEvidenceStrengthAndBaseline(boolean strong,
                                                                      InjectionEvidenceVerifier.BaselineVerdict baseline,
                                                                      FindingConfidence expected) {
        Assertions.assertThat(InjectionEvidenceVerifier.highestConfidence(strong, baseline)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, true, CONFIRMED",
            "true, true, false, TENTATIVE",
            "true, false, true, FIRM",
            "false, true, true, FIRM",
            "false, true, false, TENTATIVE"
    })
    void shouldDowngradeToTentativeWhenReplayDoesNotReproduce(boolean strong, boolean cleanBaseline, boolean reproduces,
                                                              FindingConfidence expected) {
        givenBaseline(cleanBaseline ? List.of(exchange("{\"status\":\"ok\"}")) : List.of());

        Assertions.assertThat(verifier.verify(check(strong, reproduces ? EVIDENCE : "{}"))).contains(expected);
        Assertions.assertThat(replays).hasValue(1);
    }

    @Test
    void shouldDiscardFindingWhenEvidenceIsAlreadyInBaselineWithoutReplaying() {
        givenBaseline(List.of(exchange("{\"shell\":\"" + EVIDENCE + "\"}")));

        Assertions.assertThat(verifier.verify(check(true, EVIDENCE))).isEmpty();
        Assertions.assertThat(replays).hasValue(0);
    }

    @Test
    void shouldNotReplayWhenFindingCannotRiseAboveTentative() {
        givenBaseline(List.of());

        Assertions.assertThat(verifier.verify(check(false, EVIDENCE))).contains(FindingConfidence.TENTATIVE);
        Assertions.assertThat(replays).hasValue(0);
    }

    @Test
    void shouldUseTheLatestSuccessfulExchangeAsBaselineWhenTheFreshBaselineFails() {
        givenBaseline(List.of(exchange(EVIDENCE), exchange("{\"status\":\"ok\"}")));

        Assertions.assertThat(verifier.verify(check(true, EVIDENCE))).contains(FindingConfidence.CONFIRMED);
    }

    @Test
    void shouldDiscardFindingWhenTheFreshBaselineContainsEvidenceStoredByEarlierRequests() {
        givenBaseline(List.of(exchange("[]")));
        CatsResponse polluted = CatsResponse.builder().responseCode(200).body("[{\"note\":\"" + EVIDENCE + "\"}]").build();

        Assertions.assertThat(verifier.verify(check(true, EVIDENCE, polluted))).isEmpty();
        Assertions.assertThat(replays).hasValue(0);
    }

    @Test
    void shouldPreferTheFreshBaselineOverTheRecordedExchange() {
        givenBaseline(List.of(exchange(EVIDENCE)));
        CatsResponse fresh = CatsResponse.builder().responseCode(200).body("{\"status\":\"ok\"}").build();

        Assertions.assertThat(verifier.verify(check(true, EVIDENCE, fresh))).contains(FindingConfidence.CONFIRMED);
    }

    private InjectionEvidenceVerifier.Check check(boolean strong, String replayBody) {
        return check(strong, replayBody, CatsResponse.builder().responseCode(404).body("{}").build());
    }

    private InjectionEvidenceVerifier.Check check(boolean strong, String replayBody, CatsResponse freshBaseline) {
        Supplier<CatsResponse> replay = () -> {
            replays.incrementAndGet();
            return CatsResponse.builder().responseCode(200).body(replayBody).build();
        };
        return new InjectionEvidenceVerifier.Check(data, strong, DETECTOR,
                new InjectionEvidenceVerifier.Probes(() -> freshBaseline, replay));
    }

    private void givenBaseline(List<RuntimeResourcePool.SuccessfulExchange> exchanges) {
        Mockito.when(runtimeResourcePool.successfulExchanges("/files", HttpMethod.POST)).thenReturn(exchanges);
    }

    private static RuntimeResourcePool.SuccessfulExchange exchange(String responseBody) {
        return new RuntimeResourcePool.SuccessfulExchange("/files", HttpMethod.POST, "http://localhost/files", "{}",
                200, responseBody, null, null);
    }
}
