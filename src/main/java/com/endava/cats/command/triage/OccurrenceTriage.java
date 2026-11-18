package com.endava.cats.command.triage;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.Problem;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Classifies one occurrence (one problem on one operation) with the triage strategies. Each test is classified and the
 * occurrence takes the category of most of its tests (ties: most severe, then by name), so the result does not depend
 * on test order; the representative is the lowest-numbered test of that category.
 */
final class OccurrenceTriage {
    private static final int MAX_SAMPLES = 5;
    private static final Comparator<Map.Entry<String, List<Integer>>> DOMINANT = Comparator
            .comparingInt((Map.Entry<String, List<Integer>> entry) -> -entry.getValue().size())
            .thenComparing(Map.Entry::getKey);

    private final ClusterClassifier classifier;
    private final Optional<LiveClusterVerifier> verifier;

    OccurrenceTriage(ClusterClassifier classifier, Optional<LiveClusterVerifier> verifier) {
        this.classifier = classifier;
        this.verifier = verifier;
    }

    /**
     * Triages the given occurrence from its candidates, which are in test-number order.
     */
    Optional<TriageOccurrence> triage(Problem.Occurrence occurrence, List<ClusterClassifier.Candidate> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<ClusterClassification> classifications = candidates.stream().map(classifier::classify).toList();
        int dominant = dominant(classifications);
        ClusterClassifier.Candidate representative = candidates.get(dominant);
        ClusterClassification classification = classifications.get(dominant);
        return Optional.of(TriageOccurrence.builder()
                .operation(occurrence.operation())
                .statusCodes(candidates.stream().map(candidate -> candidate.testCase().statusCode()).distinct().sorted().toList())
                .testCount(candidates.size())
                .category(classification.category())
                .severityRank(classification.severityRank())
                .diagnosis(classification.diagnosis())
                .remediation(classification.remediation())
                .fuzzers(candidates.stream().map(candidate -> candidate.testCase().fuzzer()).filter(StringUtils::isNotBlank).distinct().toList())
                .fields(occurrence.fields().stream().map(Problem.FieldCount::field).toList())
                .sampleTestIds(sampleIds(representative, candidates))
                .sample(TriageSample.from(representative))
                .curlReproducer(curlReproducer(representative.testCase()))
                .build())
                .map(triaged -> withAttribution(triaged, candidates, classification))
                .map(triaged -> withVerification(triaged, classification, representative.testCase()));
    }

    private static List<String> sampleIds(ClusterClassifier.Candidate representative, List<ClusterClassifier.Candidate> candidates) {
        return Stream.concat(Stream.of(representative), candidates.stream())
                .map(candidate -> candidate.testCase().testCase().getTestId()).filter(StringUtils::isNotBlank).distinct().limit(MAX_SAMPLES).toList();
    }

    private static int dominant(List<ClusterClassification> classifications) {
        Map<String, List<Integer>> byCategory = IntStream.range(0, classifications.size()).boxed()
                .collect(Collectors.groupingBy(index -> categoryKey(classifications.get(index)), LinkedHashMap::new, Collectors.toList()));
        return byCategory.entrySet().stream().min(DOMINANT).map(entry -> entry.getValue().getFirst()).orElseThrow();
    }

    private static String categoryKey(ClusterClassification classification) {
        return "%d|%s".formatted(classification.severityRank(), classification.category());
    }

    private static TriageOccurrence withAttribution(TriageOccurrence triaged, List<ClusterClassifier.Candidate> candidates,
                                                    ClusterClassification classification) {
        return candidates.stream().map(ClusterClassifier.Candidate::testCase).filter(test -> test.testCase().hasSameErrorAsBaseline())
                .findFirst()
                .map(baseline -> {
                    String seed = StringUtils.defaultIfBlank(baseline.testCase().getSameErrorAsBaselineFuzzer(), "HappyPath");
                    return triaged.toBuilder().attribution(TriageOccurrence.SAME_AS_BASELINE)
                            .remediation("Same %s as the unmodified request (%s, %s): fix the %s request for %s first. %s".formatted(
                                    baseline.testCase().getSameAsBaselineNoun(), baseline.testCase().getSameErrorAsBaselineTest(), seed, seed,
                                    classification.operation(), classification.remediation()))
                            .build();
                })
                .orElse(triaged);
    }

    private TriageOccurrence withVerification(TriageOccurrence triaged, ClusterClassification classification, TriageTestCase representative) {
        return verifier.map(live -> live.verify(classification, representative))
                .map(verification -> triaged.toBuilder().verificationStatus(verification.status())
                        .verifiedStatusCode(verification.statusCode()).verificationNote(verification.note()).build())
                .orElse(triaged);
    }

    private static String curlReproducer(TriageTestCase representative) {
        CatsRequest request = representative.testCase().getRequest();
        if (request == null) {
            return "";
        }
        return CatsRequest.builder()
                .headers(request.getHeaders())
                .payload(request.getPayload())
                .httpMethod(request.getHttpMethod())
                .url(StringUtils.defaultIfBlank(request.getUrl(), representative.testCase().getFullRequestPath()))
                .sendBody(request.isSendBody())
                .build()
                .toCurlCommand();
    }
}
