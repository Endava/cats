package com.endava.cats.command.triage;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.report.ErrorTemplate;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the tests of one root-cause cluster. The representative (used for the reproducer and {@code --verify}) is
 * the lowest-numbered test, because tests are accumulated in ascending test-number order.
 * <p>
 * A classification joins the cluster when it has the same cluster key. Each category chooses its key suffix (the mined
 * error template for crashes and cross-field rejections, the field, fuzzer, header or status code otherwise), and key
 * equality is transitive, so the result does not depend on test order.
 */
final class ClusterAccumulator {
    private static final int MAX_FUZZERS = 6;
    private static final int MAX_SAMPLES = 5;

    private final ClusterClassification classification;
    private final TriageTestCase representative;
    private final ErrorTemplate template;
    private final Set<String> fuzzers = new LinkedHashSet<>();
    private final List<String> sampleTestIds = new ArrayList<>();
    private int occurrences;

    ClusterAccumulator(ClusterClassification classification, TriageTestCase representative, ErrorTemplate template) {
        this.classification = classification;
        this.representative = representative;
        this.template = template;
    }

    ClusterClassification classification() {
        return classification;
    }

    TriageTestCase representative() {
        return representative;
    }

    boolean accepts(ClusterClassification candidate) {
        return classification.clusterKey().equals(candidate.clusterKey());
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
                classification.statusCode(), occurrences, classification.diagnosis(), template.id(), template.text(),
                classification.remediation(),
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
