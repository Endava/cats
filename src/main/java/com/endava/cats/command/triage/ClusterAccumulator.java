package com.endava.cats.command.triage;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.report.ErrorSimilarityDetector;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the tests of one root-cause cluster. The representative (used for the reproducer and {@code --verify}) is
 * the lowest-numbered test, because tests are accumulated in ascending test-number order.
 */
final class ClusterAccumulator {
    private static final int MAX_FUZZERS = 6;
    private static final int MAX_SAMPLES = 5;

    private final ClusterClassification classification;
    private final TriageTestCase representative;
    private final Set<String> fuzzers = new LinkedHashSet<>();
    private final List<String> sampleTestIds = new ArrayList<>();
    private int occurrences;

    ClusterAccumulator(ClusterClassification classification, TriageTestCase representative) {
        this.classification = classification;
        this.representative = representative;
    }

    ClusterClassification classification() {
        return classification;
    }

    TriageTestCase representative() {
        return representative;
    }

    boolean accepts(ClusterClassification candidate) {
        return classification.clusterKey().equals(candidate.clusterKey())
                || (classification.category().equals(candidate.category())
                && classification.operation().equals(candidate.operation())
                && classification.statusCode() == candidate.statusCode()
                && ErrorSimilarityDetector.areErrorsSimilar(classification.diagnosis(), candidate.diagnosis()));
    }

    void add(TriageTestCase testCase) {
        occurrences++;
        if (StringUtils.isNotBlank(testCase.fuzzer()) && fuzzers.size() < MAX_FUZZERS) {
            fuzzers.add(testCase.fuzzer());
        }
        String testId = testCase.testCase().getTestId();
        if (StringUtils.isNotBlank(testId) && sampleTestIds.size() < MAX_SAMPLES) {
            sampleTestIds.add(testId);
        }
    }

    RootCauseCluster toCluster(LiveClusterVerifier.Verification verification) {
        return new RootCauseCluster(classification.category(), classification.severityRank(), classification.operation(),
                classification.statusCode(), occurrences, classification.diagnosis(), classification.remediation(),
                List.copyOf(fuzzers), List.copyOf(sampleTestIds), curlReproducer(),
                verification.status(), verification.statusCode(), verification.note());
    }

    private String curlReproducer() {
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
