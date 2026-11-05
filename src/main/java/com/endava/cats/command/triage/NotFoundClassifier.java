package com.endava.cats.command.triage;

import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.util.OpenApiUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies {@code 404} failures into suspicious correlations, depleted resource pools, unresolved body references,
 * missing routes, or unresolved path parameters.
 */
final class NotFoundClassifier {
    private static final int SEVERITY = 2;
    private static final Set<String> NON_ID_SOURCE_LEAVES = Set.of("name", "title", "label", "displayname", "description");

    private final ClassificationContext context;

    NotFoundClassifier(ClassificationContext context) {
        this.context = context;
    }

    ClusterClassification classify(TriageTestCase testCase, ErrorMessage message) {
        return suspiciousCorrelation(testCase)
                .or(() -> depletedPool(testCase))
                .orElseGet(() -> OpenApiUtils.getPathVariables(testCase.path()).isEmpty()
                        ? withoutPathParameters(testCase, message)
                        : unresolvedPathParameter(testCase, message));
    }

    private static Optional<ClusterClassification> suspiciousCorrelation(TriageTestCase testCase) {
        return testCase.runtimeCorrelations().stream()
                .filter(correlation -> correlation.getTarget().location() == RequestTarget.Location.PATH)
                .filter(NotFoundClassifier::hasNonIdentifierSource)
                .findFirst()
                .map(correlation -> suspiciousCorrelationCluster(testCase, correlation));
    }

    private static ClusterClassification suspiciousCorrelationCluster(TriageTestCase testCase, ResourceCorrelation correlation) {
        String parameter = correlation.getTarget().name();
        return ClusterClassification.of("SUSPICIOUS_CORRELATION_404", SEVERITY, testCase, correlation.getSourceLocation(),
                new ClusterClassification.Explanation(
                        "Path parameter [%s] was correlated from non-ID field [%s = %s]".formatted(
                                parameter, correlation.getSourceLocation(), correlation.getValue()),
                        "Ensure collection responses expose a primary resource identifier or pin '%s' via --refData.".formatted(parameter)));
    }

    private static boolean hasNonIdentifierSource(ResourceCorrelation correlation) {
        String source = Optional.ofNullable(correlation.getSourceLocation()).orElse("");
        String leaf = source.substring(source.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return NON_ID_SOURCE_LEAVES.contains(leaf);
    }

    /**
     * Picks the most specific parent (or same) path whose first successful {@code DELETE} ran before this test.
     */
    private Optional<ClusterClassification> depletedPool(TriageTestCase testCase) {
        return context.firstDeleteTestByPath().entrySet().stream()
                .filter(entry -> entry.getValue() < testCase.number())
                .filter(entry -> testCase.path().equals(entry.getKey()) || testCase.path().startsWith(entry.getKey() + "/"))
                .max(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getKey().length()))
                .map(entry -> depletedPoolCluster(testCase, entry.getKey(), entry.getValue()));
    }

    private static ClusterClassification depletedPoolCluster(TriageTestCase testCase, String deletedPath, int deleteTestNumber) {
        return ClusterClassification.of("RESOURCE_POOL_DEPLETED_404", SEVERITY, testCase, "after:" + deletedPath,
                new ClusterClassification.Explanation(
                        "Resource pool depleted after earlier DELETE on [%s] (starting at Test %d)".formatted(deletedPath, deleteTestNumber),
                        "Run non-DELETE operations across child paths before parent DELETEs or pin an existing ID via --refData."));
    }

    private static ClusterClassification withoutPathParameters(TriageTestCase testCase, ErrorMessage message) {
        if (testCase.payloadRootObject().isEmpty() && !testCase.hasRootArrayPayload()) {
            return ClusterClassification.of("ROUTE_NOT_FOUND_404", SEVERITY, testCase, "",
                    new ClusterClassification.Explanation(
                            "%s returned 404 although the path has no path parameters (%s)".formatted(testCase.path(), message.signature()),
                            "Verify the route exists at the -s base URL (including any base path), or pin referenced query parameter values via --refData / --queryParams."));
        }
        return ClusterClassification.of("UNRESOLVED_BODY_REFERENCE_404", SEVERITY, testCase, "",
                new ClusterClassification.Explanation(
                        "Request body on %s references a resource that does not exist (%s)".formatted(testCase.path(), message.signature()),
                        "Pin existing identifiers for %s in --refData under \"%s\", or skip %s via --skipPaths."
                                .formatted(bodyIdentifierFields(testCase), testCase.path(), testCase.path())));
    }

    private static String bodyIdentifierFields(TriageTestCase testCase) {
        List<String> idFields = testCase.payloadRootObject()
                .map(PayloadFields::primitiveFields)
                .orElse(List.of()).stream()
                .filter(field -> RefDataMiner.isIdKey(field.key()))
                .map(field -> field.path().replace('.', '#'))
                .toList();
        return idFields.isEmpty() ? "the referenced body fields" : String.join(", ", idFields);
    }

    private ClusterClassification unresolvedPathParameter(TriageTestCase testCase, ErrorMessage message) {
        String producerPath = testCase.path().substring(0, Math.max(0, testCase.path().lastIndexOf("/{")));
        boolean producerInSpec = hasPostProducer(context.openAPI(), producerPath);
        boolean producerSucceeded = context.pathsWithSuccessfulPost().contains(producerPath);
        String diagnosis = producerSucceeded || !producerInSpec
                ? "Path parameter on %s was not enriched with an active resource ID (%s)".formatted(testCase.path(), message.signature())
                : "No successful 2XX POST producer recorded for [%s]".formatted(producerPath);
        return ClusterClassification.of("UNRESOLVED_PATH_PARAM_404", SEVERITY, testCase, "",
                new ClusterClassification.Explanation(diagnosis, unresolvedPathRemediation(testCase.path(), producerPath, producerInSpec)));
    }

    private String unresolvedPathRemediation(String path, String producerPath, boolean producerInSpec) {
        Map<String, Object> mined = context.minedRefData().get(path);
        if (mined != null) {
            return "Run `cats triage --export-refdata refData.yml` to pin the working IDs mined from the report: " + mined;
        }
        String parameters = OpenApiUtils.getPathVariables(path).stream().map(name -> name + ":<value>").collect(Collectors.joining(","));
        return producerInSpec && !producerPath.isEmpty()
                ? "Seed a valid resource via POST %s or pass --urlParams %s.".formatted(producerPath, parameters)
                : "Pass valid path parameters via --urlParams %s or --refData.".formatted(parameters);
    }

    private static boolean hasPostProducer(OpenAPI openAPI, String producerPath) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return true;
        }
        return Stream.ofNullable(openAPI.getPaths().get(producerPath)).map(PathItem::getPost).anyMatch(Objects::nonNull);
    }
}
