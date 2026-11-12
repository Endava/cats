package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decides how reliably a candidate security finding is caused by the injected payload.
 * <p>
 * The same detector that flagged the fuzzed response is run against a baseline response of the operation and
 * against a replay of the fuzzed request. The baseline is a freshly sent unfuzzed request, so that data stored by
 * earlier fuzzed requests (e.g. payloads returned by a collection GET) is part of it, like the "refreshed original"
 * compared by ZAP scan rules. When that request is not successful, the latest successful exchange recorded for the
 * operation is used instead.
 * <ul>
 *     <li>evidence also present in the baseline is not caused by the payload, so the finding is discarded;</li>
 *     <li>{@link FindingConfidence#CONFIRMED}: strong evidence, absent from the baseline, reproduced on replay;</li>
 *     <li>{@link FindingConfidence#FIRM}: reproduced on replay, with strong evidence but no baseline, or weak evidence absent from the baseline;</li>
 *     <li>{@link FindingConfidence#TENTATIVE}: everything else, including evidence that does not reproduce.</li>
 * </ul>
 * The replay is only sent when it can raise the confidence above {@link FindingConfidence#TENTATIVE}.
 */
@Singleton
public class InjectionEvidenceVerifier {
    private final RuntimeResourcePool runtimeResourcePool;

    public InjectionEvidenceVerifier(RuntimeResourcePool runtimeResourcePool) {
        this.runtimeResourcePool = runtimeResourcePool;
    }

    /**
     * Verifies a candidate finding.
     *
     * @param check the candidate finding and the means to re-evaluate it
     * @return the confidence of the finding, or empty when the evidence is already present in the baseline response
     */
    public Optional<FindingConfidence> verify(Check check) {
        return verifyCustomReplay(check, () -> check.detector().test(check.probes().fuzzed().get()));
    }

    /**
     * Verifies a candidate finding whose replay check uses an independent confirmation probe (e.g. a second
     * arithmetic canary pair) while still verifying baseline novelty via {@code check.detector()}.
     *
     * @param check       the candidate finding and baseline check
     * @param replayCheck executes the confirmation replay and returns true when the finding reproduces
     * @return the confidence of the finding, or empty when the evidence is already present in the baseline response
     */
    public Optional<FindingConfidence> verifyCustomReplay(Check check, BooleanSupplier replayCheck) {
        BaselineVerdict baseline = baselineVerdict(check);
        if (baseline == BaselineVerdict.PRESENT) {
            return Optional.empty();
        }
        FindingConfidence highest = highestConfidence(check.strongEvidence(), baseline);
        boolean confirmedByReplay = highest.isReliable() && replayCheck.getAsBoolean();
        return Optional.of(confirmedByReplay ? highest : FindingConfidence.TENTATIVE);
    }

    /**
     * Resolves the effective unfuzzed payload for {@code data}, applying any recorded baseline or runtime resource
     * enrichment so differential payloads append to the same field values sent by {@code replayOriginal}.
     *
     * @param data the fuzzing data of the operation
     * @return the effective baseline payload string
     */
    public String effectiveBaselinePayload(FuzzingData data) {
        if (data == null || data.getHeaders() == null) {
            return data == null ? null : data.getPayload();
        }
        try {
            ServiceData preview = ServiceData.from(data).build();
            String base = Optional.ofNullable(runtimeResourcePool.applySuccessfulRequestBaseline(preview))
                    .orElse(data.getPayload());
            return Optional.ofNullable(runtimeResourcePool.previewEnrichedPayload(preview, base))
                    .orElse(base);
        } catch (RuntimeException _) {
            return data.getPayload();
        }
    }

    static FindingConfidence highestConfidence(boolean strongEvidence, BaselineVerdict baseline) {
        return switch (baseline) {
            case NOVEL -> strongEvidence ? FindingConfidence.CONFIRMED : FindingConfidence.FIRM;
            case ABSENT, PRESENT -> strongEvidence ? FindingConfidence.FIRM : FindingConfidence.TENTATIVE;
        };
    }

    private BaselineVerdict baselineVerdict(Check check) {
        return baseline(check)
                .map(baseline -> check.detector().test(baseline) ? BaselineVerdict.PRESENT : BaselineVerdict.NOVEL)
                .orElse(BaselineVerdict.ABSENT);
    }

    private Optional<CatsResponse> baseline(Check check) {
        CatsResponse fresh = check.probes().original().get();
        return ResponseCodeFamily.is2xxCode(fresh.getResponseCode()) ? Optional.of(fresh) : recordedBaseline(check.data());
    }

    private Optional<CatsResponse> recordedBaseline(FuzzingData data) {
        List<RuntimeResourcePool.SuccessfulExchange> exchanges =
                runtimeResourcePool.successfulExchanges(data.getPath(), data.getMethod());
        return exchanges.isEmpty() ? Optional.empty() : Optional.of(toResponse(exchanges.getLast()));
    }

    private static CatsResponse toResponse(RuntimeResourcePool.SuccessfulExchange exchange) {
        return CatsResponse.builder()
                .responseCode(exchange.status())
                .body(Objects.toString(exchange.responseBody(), ""))
                .build();
    }

    /**
     * A candidate finding to verify.
     *
     * @param data           the fuzzing data of the operation under test
     * @param strongEvidence whether the evidence is an artefact that a plain echo of the payload cannot produce
     * @param detector       re-evaluates whether a response carries the same evidence
     * @param probes         the requests used to verify the finding
     */
    public record Check(FuzzingData data, boolean strongEvidence, Predicate<CatsResponse> detector, Probes probes) {
    }

    /**
     * Requests sent to verify a finding.
     *
     * @param original sends the unfuzzed request of the operation and returns its response
     * @param fuzzed   re-sends the fuzzed request and returns its response
     */
    public record Probes(Supplier<CatsResponse> original, Supplier<CatsResponse> fuzzed) {
    }

    enum BaselineVerdict {
        ABSENT, NOVEL, PRESENT
    }
}
