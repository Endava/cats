package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.report.error.extract.EmptyBody;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Renders problems into {@link ProblemCard}s. Only problems identified by the server message show it, with a sample
 * response; the other kinds show their violation, status or evidence, never a body-derived headline.
 */
final class ProblemCards {
    static final int MAX_LINKS_PER_OPERATION = 20;
    private static final int MAX_LISTED_FUZZERS = 3;
    private static final int MAX_LISTED_REASONS = 3;

    private ProblemCards() {
    }

    static List<ProblemCard> of(List<Problem> problems, Map<String, CatsTestCaseSummary> testsById, boolean javascript) {
        Context context = new Context(problems, anchors(problems), testsById, javascript);
        return IntStream.range(0, problems.size()).mapToObj(context::card).toList();
    }

    /**
     * The anchor of every problem: {@code problem-<id>}, suffixed in the unlikely case of an id collision.
     */
    private static List<String> anchors(List<Problem> problems) {
        Map<String, Integer> seen = new HashMap<>();
        return problems.stream().map(problem -> "problem-" + problem.id())
                .map(anchor -> seen.merge(anchor, 1, Integer::sum) == 1 ? anchor : anchor + "-" + seen.get(anchor))
                .toList();
    }

    static String kindLabel(ProblemKind kind) {
        return switch (kind) {
            case SERVER_ERROR -> "Server error";
            case ERROR_MESSAGE -> "Rejected";
            case RESPONSE_VIOLATION -> "Response violation";
            case STATUS_CODE -> "Status code";
            case ACCEPTED_REQUEST -> "Accepted request";
            case NO_RESPONSE -> "No response";
        };
    }

    private record Context(List<Problem> problems, List<String> anchors, Map<String, CatsTestCaseSummary> testsById, boolean javascript) {

        ProblemCard card(int index) {
            Problem problem = problems.get(index);
            boolean message = problem.kind().isIdentifiedByServerMessage();
            return new ProblemCard(index + 1, problem.id(), anchors.get(index), problem.kind().name(), kindLabel(problem.kind()),
                    problem.level(), problem.title(), meta(problem), message ? problem.errorTemplate() : null,
                    message ? ProblemMessage.of(problem, tests(problem).toList()) : null, evidence(problem),
                    baselineNote(problem), baselineTests(problem), groupedBy(problem), rows(problem), variantsLabel(problem), variantRows(problem),
                    links(problem), message ? sampleResponse(problem) : null, problem.testCount());
        }

        private List<ProblemCard.TestLink> baselineTests(Problem problem) {
            return tests(problem).map(CatsTestCaseSummary::getSameErrorAsBaselineTest)
                    .filter(StringUtils::isNotBlank).distinct().filter(testsById::containsKey)
                    .map(ProblemCards::link).toList();
        }

        private List<ProblemCard.OperationRow> rows(Problem problem) {
            return problem.occurrences().stream().map(occurrence -> row(problem, occurrence)).toList();
        }

        private ProblemCard.OperationRow row(Problem problem, Problem.Occurrence occurrence) {
            List<ProblemCard.TestLink> links = occurrence.testIds().stream().map(ProblemCards::link).toList();
            boolean truncated = links.size() > MAX_LINKS_PER_OPERATION;
            String fields = occurrence.fields().stream().map(field -> "%s (%d)".formatted(field.field(), field.tests())).collect(Collectors.joining(", "));
            return new ProblemCard.OperationRow(occurrence.operation(), problem.id(), links.size(), fields,
                    truncated ? links.subList(0, MAX_LINKS_PER_OPERATION) : links, truncated && !javascript ? links : List.of());
        }

        private List<ProblemCard.CrossLink> links(Problem problem) {
            return problem.alsoIn().stream().map(this::indexOf).flatMap(Optional::stream)
                    .map(index -> new ProblemCard.CrossLink(anchors.get(index), "%s also in problem %d: %s".formatted(
                            sharesServerMessage(problem, problems.get(index)) ? "Same server message" : "Same fuzzer", index + 1, problems.get(index).title())))
                    .toList();
        }

        private Optional<Integer> indexOf(String problemId) {
            return IntStream.range(0, problems.size()).filter(index -> problems.get(index).id().equals(problemId)).boxed().findFirst();
        }

        private boolean sharesServerMessage(Problem problem, Problem other) {
            Set<String> messages = serverMessageIds(problem);
            return serverMessageIds(other).stream().anyMatch(messages::contains);
        }

        private Set<String> serverMessageIds(Problem problem) {
            return tests(problem).map(CatsTestCaseSummary::getErrorTemplateId).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        }

        private Stream<CatsTestCaseSummary> tests(Problem problem) {
            return problem.occurrences().stream().flatMap(occurrence -> occurrence.testIds().stream()).map(testsById::get).filter(Objects::nonNull);
        }

        private String sampleResponse(Problem problem) {
            return tests(problem).findFirst().map(CatsTestCaseSummary::getResponseBody).filter(StringUtils::isNotBlank).orElse(EmptyBody.PLACEHOLDER);
        }
    }

    private static ProblemCard.TestLink link(String testId) {
        return new ProblemCard.TestLink(testId.replace(" ", ""), testId);
    }

    private static String meta(Problem problem) {
        List<String> fuzzers = problem.occurrences().stream().flatMap(occurrence -> occurrence.fuzzers().stream()).distinct().toList();
        int operations = problem.occurrences().size();
        return String.join(" · ", reasons(problem),
                "%d %s".formatted(problem.testCount(), ProblemPage.plural(problem.testCount(), "test")),
                "%d %s".formatted(operations, ProblemPage.plural(operations, "operation")),
                "%d %s".formatted(fuzzers.size(), ProblemPage.plural(fuzzers.size(), "fuzzer"))
                        + (fuzzers.size() <= MAX_LISTED_FUZZERS ? " (%s)".formatted(String.join(", ", fuzzers)) : ""));
    }

    private static String reasons(Problem problem) {
        List<String> reasons = problem.reasons();
        String listed = String.join(" · ", reasons.subList(0, Math.min(reasons.size(), MAX_LISTED_REASONS)));
        return reasons.size() > MAX_LISTED_REASONS ? "%s · %d more reasons".formatted(listed, reasons.size() - MAX_LISTED_REASONS) : listed;
    }

    private static String evidence(Problem problem) {
        return Optional.ofNullable(problem.evidence())
                .map(evidence -> "%s%s: %s".formatted(evidence.testId(),
                        evidence.confidence() == null ? "" : ", confidence " + evidence.confidence(), StringUtils.defaultString(evidence.details())))
                .orElse(null);
    }

    private static String baselineNote(Problem problem) {
        return problem.sameAsBaseline() == 0 ? null : "%d of %d also fail the same way on the unmodified request: fix that request first"
                .formatted(problem.sameAsBaseline(), problem.testCount());
    }

    private static String groupedBy(Problem problem) {
        String status = problem.occurrences().stream().flatMap(occurrence -> occurrence.statuses().stream()).findFirst().map(String::valueOf).orElse("");
        return switch (problem.kind()) {
            case SERVER_ERROR, ERROR_MESSAGE -> messageGrouping(problem, status);
            case RESPONSE_VIOLATION -> problem.variants().isEmpty() ? "same violation"
                    : "same declared response schema; %d different %s".formatted(problem.variants().size(), ProblemPage.plural(problem.variants().size(), "violation"));
            case STATUS_CODE -> problem.variants().isEmpty() ? "same status code"
                    : "same status code; %d different server %s".formatted(problem.variants().size(), ProblemPage.plural(problem.variants().size(), "message"));
            case ACCEPTED_REQUEST -> "%s, request accepted (%sxx); the response body is %s and is not used for grouping".formatted(
                    problem.discriminator().startsWith("Unexpected status") ? "same fuzzer" : "same finding",
                    status.isEmpty() ? "2" : status.substring(0, 1),
                    problem.discriminator().startsWith("Unexpected status") ? "the returned resource" : "evidence, not identity,");
            case NO_RESPONSE -> "same result reason; no HTTP response was judged";
        };
    }

    private static String messageGrouping(Problem problem, String status) {
        String observation = problem.testCount() == 1 ? "Observed in one test on one operation."
                : "%d tests share this server-message pattern and HTTP %s across %d %s.".formatted(problem.testCount(), status,
                problem.occurrences().size(), ProblemPage.plural(problem.occurrences().size(), "operation"));
        return observation + (problem.kind() == ProblemKind.ERROR_MESSAGE
                ? " Rejections also stay separate when CATS judged a different kind of finding."
                : " Different server-message patterns or HTTP statuses are kept separate.");
    }

    private static String variantsLabel(Problem problem) {
        if (problem.variants().isEmpty()) {
            return null;
        }
        String noun = problem.kind() == ProblemKind.STATUS_CODE ? "Server messages" : "Violations";
        return "%s (%d)".formatted(noun, problem.variants().size());
    }

    private static List<ProblemCard.VariantRow> variantRows(Problem problem) {
        return problem.variants().stream()
                .map(variant -> new ProblemCard.VariantRow(StringUtils.defaultIfBlank(variant.text(), "(message not available)"), variant.count()))
                .toList();
    }
}
