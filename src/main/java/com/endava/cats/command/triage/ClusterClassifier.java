package com.endava.cats.command.triage;

import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.report.ErrorSimilarityDetector;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Assigns each failing test exactly one root-cause category. Strategies are evaluated in a fixed precedence order
 * (crash, header collision, auth, not-found, fuzzed-field domain rejection, cross-field, validation bypass) and
 * fall back to {@code CONTRACT_MISMATCH}.
 */
final class ClusterClassifier {
    private final ClassificationContext context;
    private final NotFoundClassifier notFoundClassifier;

    ClusterClassifier(ClassificationContext context) {
        this.context = context;
        this.notFoundClassifier = new NotFoundClassifier(context);
    }

    /**
     * A failing test together with its (optional) failure analysis and extracted error message.
     */
    record Candidate(TriageTestCase testCase, ErrorMessage message, Optional<FailureAnalysis> analysis) {
    }

    ClusterClassification classify(Candidate candidate) {
        return List.<Function<Candidate, Optional<ClusterClassification>>>of(
                        ClusterClassifier::serverCrash,
                        ClusterClassifier::headerCollision,
                        ClusterClassifier::authGap,
                        this::notFound,
                        ClusterClassifier::fieldDomainConstraint,
                        this::crossFieldConstraint,
                        ClusterClassifier::validationBypass).stream()
                .map(strategy -> strategy.apply(candidate))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> contractMismatch(candidate));
    }

    private static Optional<ClusterClassification> serverCrash(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        if (!ResponseCodeFamily.is5xxCode(testCase.statusCode())) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("SERVER_CRASH_5XX", 0, testCase, clusterKey(testCase),
                new ClusterClassification.Explanation(
                        "Unhandled %d server error (%s)".formatted(testCase.statusCode(), candidate.message().signature()),
                        "Inspect the server stacktrace for %s, then verify the fix with `cats triage --verify` or `cats replay %s --stateful`."
                                .formatted(testCase.operation(), testId(testCase)))));
    }

    /**
     * Only {@code 4XX} rejections are attributed to a runtime-correlated header; a correlated header on a {@code 5XX}
     * or accepted request is not the root cause.
     */
    private static Optional<ClusterClassification> headerCollision(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        if (!ResponseCodeFamily.is4xxCode(testCase.statusCode())) {
            return Optional.empty();
        }
        return testCase.runtimeCorrelations().stream()
                .filter(correlation -> correlation.getTarget().location() == RequestTarget.Location.HEADER)
                .findFirst()
                .map(correlation -> headerCollisionCluster(testCase, correlation));
    }

    private static ClusterClassification headerCollisionCluster(TriageTestCase testCase, ResourceCorrelation correlation) {
        String header = correlation.getTarget().name();
        return ClusterClassification.of("HEADER_CORRELATION_COLLISION", 1, testCase, header,
                new ClusterClassification.Explanation(
                        "Header [%s] was overwritten by runtime correlation from [%s]".formatted(header, correlation.getSourceLocation()),
                        "Pin header '%s' via -H or headers.yml under `all` or the path key (method-scoped keys are not protected from runtime correlation)."
                                .formatted(header)));
    }

    private static Optional<ClusterClassification> authGap(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        int code = testCase.statusCode();
        if ((code != 401 && code != 403) || testCase.isFuzzer(BypassAuthenticationFuzzer.class)) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("AUTH_CONFIGURATION_GAP", 2, testCase, String.valueOf(code),
                new ClusterClassification.Explanation(
                        "Endpoint rejected credentials with HTTP %d (%s)".formatted(code, candidate.message().signature()),
                        "Pass valid credentials via -H 'Authorization=$$API_AUTH' (the env placeholder must be the whole value), --headers, --wfcAuth or --basicAuth.")));
    }

    private Optional<ClusterClassification> notFound(Candidate candidate) {
        return candidate.testCase().statusCode() == 404
                ? Optional.of(notFoundClassifier.classify(candidate.testCase(), candidate.message()))
                : Optional.empty();
    }

    private static Optional<ClusterClassification> fieldDomainConstraint(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        return candidate.analysis()
                .flatMap(FailureAnalysis::protectedField)
                .map(field -> ClusterClassification.of("FIELD_DOMAIN_CONSTRAINT_4XX", 1, testCase, field,
                        Remediations.fieldDomain(testCase, field, candidate.message())));
    }

    private Optional<ClusterClassification> crossFieldConstraint(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        boolean validationRejection = testCase.statusCode() == 400 || testCase.statusCode() == 422;
        boolean seedRejection = isSeedFuzzer(testCase)
                || (FailureAnalyzer.expectedToSucceed(testCase) && context.pathsWithRules().contains(testCase.path()));
        if (!validationRejection || !seedRejection) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("CROSS_FIELD_CONSTRAINT_4XX", 1, testCase, clusterKey(testCase),
                Remediations.crossField(testCase, candidate.message(), context)));
    }

    private static Optional<ClusterClassification> validationBypass(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        if (!ResponseCodeFamily.is2xxCode(testCase.statusCode()) || !testCase.expected().expectsOnly4xx()) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("VALIDATION_BYPASS_2XX", 2, testCase, testCase.fuzzer(),
                new ClusterClassification.Explanation(
                        "Fuzzer [%s] expected 4XX rejection, but server accepted input with HTTP %d".formatted(testCase.fuzzer(), testCase.statusCode()),
                        Remediations.validationBypass(testCase))));
    }

    private ClusterClassification contractMismatch(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        String reason = StringUtils.defaultIfBlank(testCase.testCase().getResultReason(), "Unexpected response " + testCase.statusCode());
        boolean undocumented = context.drift().isUndocumented(testCase);
        ClusterClassification classification = ClusterClassification.of("CONTRACT_MISMATCH", 3, testCase,
                testCase.statusCode() + "|" + reason,
                new ClusterClassification.Explanation(
                        "%s (HTTP %d; %s)".formatted(reason, testCase.statusCode(), candidate.message().signature()),
                        Remediations.contractMismatch(testCase, undocumented)));
        return undocumented ? classification.notLiveVerifiable() : classification;
    }

    private static boolean isSeedFuzzer(TriageTestCase testCase) {
        return testCase.isFuzzer(HappyPathFuzzer.class) || testCase.isFuzzer(OnlyRequiredFieldsFuzzer.class);
    }

    private static String clusterKey(TriageTestCase testCase) {
        return ErrorSimilarityDetector.extractNormalizedClusterKey(
                testCase.rawResponseBody(), testCase.testCase().getResultReason());
    }

    static String testId(TriageTestCase testCase) {
        return StringUtils.deleteWhitespace(StringUtils.defaultIfBlank(testCase.testCase().getTestId(), "Test" + testCase.number()));
    }
}
