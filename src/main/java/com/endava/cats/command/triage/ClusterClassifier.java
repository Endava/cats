package com.endava.cats.command.triage;

import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Assigns each failing test one triage category. Recorded limitations, response checks and security evidence take
 * precedence over status-based hypotheses. These categories do not change the original verdict or problem identity.
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
                        ResponseObservationClassifier::classify,
                        ClusterClassifier::notImplemented,
                        ClusterClassifier::serverCrash,
                        ResponseObservationClassifier::assertion,
                        SecurityTriageClassifier::classify,
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

    private static Optional<ClusterClassification> notImplemented(Candidate candidate) {
        TriageTestCase test = candidate.testCase();
        return test.statusCode() == 501 ? Optional.of(ClusterClassification.of("METHOD_NOT_IMPLEMENTED_501", 0, test,
                new ClusterClassification.Explanation("The server returned HTTP 501: requested functionality is not implemented.",
                        "Check support for %s and the expected status in the sample; inspect routing or gateway behavior. HTTP 501 alone does not demonstrate a server crash."
                                .formatted(test.operation())))) : Optional.empty();
    }

    private static Optional<ClusterClassification> serverCrash(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        if (!ResponseCodeFamily.is5xxCode(testCase.statusCode())) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("SERVER_CRASH_5XX", 0, testCase,
                new ClusterClassification.Explanation(
                        "HTTP %d server error observed (%s)".formatted(testCase.statusCode(), candidate.message().signature()),
                        "Inspect server and gateway logs for %s using the sample request, then check the response with `cats triage --verify` or `cats replay %s --stateful`."
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
        return ClusterClassification.of("HEADER_CORRELATION_COLLISION", 1, testCase,
                new ClusterClassification.Explanation(
                        "The rejected request used header [%s] from runtime correlation [%s]; this may explain the rejection.".formatted(header, correlation.getSourceLocation()),
                        "Check the correlated value against the server message. If inappropriate, pin header '%s' via -H or headers.yml under `all` or the path key (method-scoped keys are not protected from runtime correlation)."
                                .formatted(header)));
    }

    private static Optional<ClusterClassification> authGap(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        int code = testCase.statusCode();
        if ((code != 401 && code != 403) || testCase.isFuzzer(BypassAuthenticationFuzzer.class) || testCase.expected().allows(code)) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("AUTH_CONFIGURATION_GAP", 2, testCase,
                new ClusterClassification.Explanation(
                        "Unexpected HTTP %d authentication or authorization rejection (%s)".formatted(code, candidate.message().signature()),
                        "Check credentials, permissions and gateway policies. Supply credentials via -H 'Authorization=$$API_AUTH' (the env placeholder must be the whole value), --headers, --wfcAuth or --basicAuth.")));
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
                .map(field -> ClusterClassification.of("FIELD_DOMAIN_CONSTRAINT_4XX", 1, testCase,
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
        return Optional.of(ClusterClassification.of("CROSS_FIELD_CONSTRAINT_4XX", 1, testCase,
                Remediations.crossField(testCase, candidate.message(), context)));
    }

    private static Optional<ClusterClassification> validationBypass(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        if (!ResponseCodeFamily.is2xxCode(testCase.statusCode()) || !testCase.expected().expectsOnly4xx()) {
            return Optional.empty();
        }
        return Optional.of(ClusterClassification.of("VALIDATION_BYPASS_2XX", 2, testCase,
                new ClusterClassification.Explanation(
                        "Fuzzer [%s] expected 4XX rejection, but server accepted input with HTTP %d".formatted(testCase.fuzzer(), testCase.statusCode()),
                        Remediations.validationBypass(testCase))));
    }

    private ClusterClassification contractMismatch(Candidate candidate) {
        TriageTestCase testCase = candidate.testCase();
        String reason = StringUtils.defaultIfBlank(testCase.testCase().getResultReason(), "Unexpected response " + testCase.statusCode());
        boolean undocumented = context.drift().isUndocumented(testCase);
        ClusterClassification classification = ClusterClassification.of("CONTRACT_MISMATCH", 3, testCase,
                new ClusterClassification.Explanation(
                        "%s (HTTP %d; %s)".formatted(reason, testCase.statusCode(), candidate.message().signature()),
                        Remediations.contractMismatch(testCase, undocumented)));
        return undocumented ? classification.notLiveVerifiable() : classification;
    }

    private static boolean isSeedFuzzer(TriageTestCase testCase) {
        return testCase.isFuzzer(HappyPathFuzzer.class) || testCase.isFuzzer(OnlyRequiredFieldsFuzzer.class);
    }

    static String testId(TriageTestCase testCase) {
        return StringUtils.deleteWhitespace(StringUtils.defaultIfBlank(testCase.testCase().getTestId(), "Test" + testCase.number()));
    }
}
