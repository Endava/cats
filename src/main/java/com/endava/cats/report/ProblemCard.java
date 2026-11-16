package com.endava.cats.report;

import java.util.List;

/**
 * One problem card of the summary report, ready to render.
 *
 * @param rank           the rank of the problem in this report
 * @param id             the problem id, shown in the card footer
 * @param anchor         the unique HTML anchor of the card
 * @param kind           the problem kind
 * @param kindLabel      the problem kind in words
 * @param level          {@code error} or {@code warn}
 * @param title          the kind-specific title
 * @param meta           reasons, tests, operations and fuzzers in one line
 * @param serverMessage  the server message, only for problems identified by it
 * @param evidence       the evidence of the most confident test, or {@code null}
 * @param baselineNote   how many tests also fail on the unmodified request, or {@code null}
 * @param groupedBy      why the tests were grouped
 * @param rows           the operations × mutated fields table
 * @param variantsLabel  e.g. {@code Server messages (12)}, or {@code null} without variants
 * @param variants       the distinct server messages or violations
 * @param related        related problems
 * @param sampleResponse a sample response body, only for problems identified by the server message
 * @param testCount      the number of tests
 */
public record ProblemCard(int rank, String id, String anchor, String kind, String kindLabel, String level, String title,
                          String meta, String serverMessage, String evidence, String baselineNote, String groupedBy,
                          List<OperationRow> rows, String variantsLabel, List<VariantRow> variants, List<CrossLink> related,
                          String sampleResponse, int testCount) {

    /**
     * The card heading: the title without the server message for problems identified by it, because the card shows the
     * message on its own line.
     *
     * @return the heading
     */
    public String heading() {
        String suffix = ": " + serverMessage;
        return serverMessage != null && title.endsWith(suffix) ? title.substring(0, title.length() - suffix.length()) : title;
    }

    public String levelLabel() {
        return "error".equals(level) ? "ERROR" : "WARN";
    }

    public boolean is5xx() {
        return "SERVER_ERROR".equals(kind);
    }

    public boolean hasServerMessage() {
        return serverMessage != null;
    }

    public boolean hasEvidence() {
        return evidence != null;
    }

    public boolean hasBaselineNote() {
        return baselineNote != null;
    }

    public boolean hasVariants() {
        return variantsLabel != null;
    }

    public boolean hasRelated() {
        return !related.isEmpty();
    }

    public boolean hasSampleResponse() {
        return sampleResponse != null;
    }

    public String testsLabel() {
        return "View %d %s".formatted(testCount, ProblemPage.plural(testCount, "test"));
    }

    /**
     * One operation of a problem with its mutated fields and test links.
     *
     * @param operation the HTTP method and path
     * @param problemId the problem id, for the table filter
     * @param tests     the number of tests
     * @param fields    the mutated fields with their test counts, or an empty string
     * @param links     at most {@link ProblemCards#MAX_LINKS_PER_OPERATION} test links
     * @param allLinks  every test link, when there are more than shown, for reports without JavaScript
     */
    public record OperationRow(String operation, String problemId, int tests, String fields, List<TestLink> links,
                               List<TestLink> allLinks) {
        public boolean hasFields() {
            return !fields.isEmpty();
        }

        public boolean hasMore() {
            return tests > links.size();
        }

        public String viewAllLabel() {
            return "view all %d".formatted(tests);
        }
    }

    /**
     * A link to a test page.
     *
     * @param key the test key, e.g. {@code Test12}
     * @param id  the test id, e.g. {@code Test 12}
     */
    public record TestLink(String key, String id) {
    }

    /**
     * A distinct server message or violation of a problem.
     *
     * @param text  the server message or violation
     * @param count the number of tests
     */
    public record VariantRow(String text, int count) {
    }

    /**
     * A related problem.
     *
     * @param anchor the anchor of its card
     * @param label  why it is related, its rank and its title
     */
    public record CrossLink(String anchor, String label) {
    }
}
