package com.endava.cats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Represents a summary of a CATS test case.
 * This summary includes information such as test case status, path, and execution time.
 */
@EqualsAndHashCode
@Getter
public class CatsTestCaseSummary implements Comparable<CatsTestCaseSummary> {
    private String scenario;
    private String result;
    private String resultReason;
    private String id;
    private String fuzzer;
    private String path;
    private String resultDetails;
    private String responseBody;
    private double timeToExecuteInSec;
    private long timeToExecuteInMs;
    private String httpMethod;
    private boolean switchedResult;
    private int httpResponseCode;
    private FindingConfidence confidence;
    private String errorTemplateId;
    private String problemId;
    private String sameErrorAsBaselineTest;
    private String sameErrorAsBaselineFuzzer;
    // Gson must exclude this in-memory state from every serializer, including plain Gson.
    @SuppressWarnings("java:S2065")
    private transient RequestVocabulary requestVocabulary;
    // Gson must exclude this in-memory state from every serializer, including plain Gson.
    @SuppressWarnings("java:S2065")
    private transient ErrorSignature errorSignature;
    // The template text is persisted once per problem, not per test.
    @SuppressWarnings("java:S2065")
    private transient String errorTemplate;
    // The mutated fields are persisted per problem occurrence, not per test.
    @SuppressWarnings("java:S2065")
    private transient List<String> mutatedFields;

    /**
     * Creates a CatsTestCaseSummary object from a CatsTestCase.
     *
     * @param testCase The CatsTestCase to generate the summary from.
     * @return A CatsTestCaseSummary representing the summary of the provided CatsTestCase.
     */
    public static CatsTestCaseSummary fromCatsTestCase(CatsTestCase testCase) {
        CatsTestCaseSummary summary = new CatsTestCaseSummary();
        summary.id = testCase.getTestId();
        summary.scenario = testCase.getScenario();
        summary.result = testCase.getResult();
        summary.fuzzer = testCase.getFuzzer();
        summary.path = testCase.getContractPath();
        summary.resultReason = testCase.getResultReason();
        summary.resultDetails = testCase.getResultDetails();
        summary.timeToExecuteInSec = testCase.getResponse().getResponseTimeInMs() / 1000d;
        summary.timeToExecuteInMs = testCase.getResponse().getResponseTimeInMs();
        summary.httpMethod = testCase.getRequest().getHttpMethod().toLowerCase(Locale.ROOT);
        summary.switchedResult = testCase.getResultIgnoreDetails() != null;
        summary.httpResponseCode = testCase.getResponse().getResponseCode();
        summary.confidence = testCase.getConfidence();
        summary.sameErrorAsBaselineTest = testCase.getSameErrorAsBaselineTest();
        summary.sameErrorAsBaselineFuzzer = testCase.getSameErrorAsBaselineFuzzer();
        String response = Optional.ofNullable(testCase.getResponse().getBody()).orElse("[Empty response body]");
        summary.responseBody = response.substring(0, Math.min(response.length(), 1000));
        summary.mutatedFields = Optional.ofNullable(testCase.getRequestProvenance())
                .map(RequestProvenance::getMutationTargets).orElse(List.of()).stream()
                .sorted(RequestTarget.ordering()).map(RequestTarget::label).toList();
        summary.requestVocabulary = summary.getError() || summary.getWarning()
                ? RequestVocabulary.from(testCase.getRequest().getPayload(), testCase.getRequest().getUrl(), testCase.getContractPath())
                : RequestVocabulary.EMPTY;

        return summary;
    }

    /**
     * Records the error template mined for this (failing or warning) test case. Pass {@code null}s to drop a template
     * read from an older report for a response that carries no error message.
     *
     * @param templateId the stable template identifier
     * @param template   the human-readable template text
     */
    public void assignErrorTemplate(String templateId, String template) {
        this.errorTemplateId = templateId;
        this.errorTemplate = template;
    }

    /**
     * Records the problem this (failing or warning) test case belongs to.
     *
     * @param problemId the id of the problem
     */
    public void assignProblem(String problemId) {
        this.problemId = problemId;
    }

    /**
     * Records the mutated request fields of summaries read back from a report, whose per-test files carry them.
     *
     * @param fields the mutated fields, e.g. {@code body.description}
     */
    public void attachMutatedFields(List<String> fields) {
        this.mutatedFields = List.copyOf(fields);
    }

    /**
     * Returns the mutated request fields, or an empty list for summaries read from a report file.
     *
     * @return the mutated fields, e.g. {@code body.description}
     */
    public List<String> getMutatedFields() {
        return Optional.ofNullable(mutatedFields).orElse(List.of());
    }

    /**
     * Returns the mined error template text, or {@code null} for summaries read from a report file, which keep only
     * the template id; the text is persisted once in the problem.
     *
     * @return the error template text
     */
    public String getErrorTemplate() {
        return errorTemplate;
    }

    /**
     * Records the error signature computed from the full response body, which {@link #getResponseBody()} only keeps
     * the first 1000 characters of.
     *
     * @param signature the error signature of the full response
     */
    public void attachErrorSignature(ErrorSignature signature) {
        this.errorSignature = signature;
    }

    /**
     * Returns the error signature of the full response body, or empty for summaries read from a report file.
     *
     * @return the error signature
     */
    public Optional<ErrorSignature> getErrorSignature() {
        return Optional.ofNullable(errorSignature);
    }

    /**
     * Returns the vocabulary of the request sent by this test case, or an empty one for summaries read from a report file.
     *
     * @return the request vocabulary
     */
    public RequestVocabulary getRequestVocabulary() {
        return Optional.ofNullable(requestVocabulary).orElse(RequestVocabulary.EMPTY);
    }

    @Override
    public int compareTo(CatsTestCaseSummary o) {
        String o1StringPart = this.id.replaceAll("\\d", "");
        String o2StringPart = o.id.replaceAll("\\d", "");

        if (o1StringPart.equalsIgnoreCase(o2StringPart)) {
            return Integer.compare(extractInt(this.id), extractInt(o.id));
        }
        return this.id.compareTo(o.id);
    }

    private int extractInt(String s) {
        String num = s.replaceAll("\\D", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    /**
     * Gets a key derived from the test ID with spaces removed.
     *
     * @return The key for the test.
     */
    public String getKey() {
        return id.replace(" ", "");
    }

    /**
     * Checks if the test result is an error.
     *
     * @return True if the result is an error, false otherwise.
     */
    public boolean getError() {
        return this.result != null && this.result.equalsIgnoreCase("error");
    }

    /**
     * Checks if the test result is a warning.
     *
     * @return True if the result is a warning, false otherwise.
     */
    public boolean getWarning() {
        return this.result != null
                && (this.result.equalsIgnoreCase(CatsResultFactory.WARN) || this.result.equalsIgnoreCase("warning"));
    }

    /**
     * Checks of the current response is a 9xx response.
     * 9xx responses are used to signal that the test case was not executed due to technical issues.
     *
     * @return True if the response code is between 900 and 999, false otherwise.
     */
    public boolean is9xxResponse() {
        return this.httpResponseCode >= 900 && this.httpResponseCode < 1000;
    }
}
