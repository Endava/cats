package com.endava.cats.report.problem;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.RequestTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic failing tests shaped like real reports, for problem tests. Real reports are private and never committed.
 */
public final class ProblemFixtures {
    private ProblemFixtures() {
    }

    public static Fixture test(int number) {
        return new Fixture(number);
    }

    /**
     * A fluent builder of one test case summary.
     */
    public static final class Fixture {
        private final int number;
        private String result = "warn";
        private String reason = "Unexpected response code: 200";
        private int status = 200;
        private String fuzzer = "Fuzzer";
        private String method = "POST";
        private String path = "/orders";
        private String details = "";
        private String body = "{}";
        private FindingConfidence confidence;
        private String templateId;
        private String template;
        private String baselineTest;
        private final List<RequestTarget> fields = new ArrayList<>();

        private Fixture(int number) {
            this.number = number;
        }

        public Fixture error() {
            this.result = "error";
            return this;
        }

        public Fixture reason(String reason, int status) {
            this.reason = reason;
            this.status = status;
            return this;
        }

        public Fixture fuzzer(String fuzzer) {
            this.fuzzer = fuzzer;
            return this;
        }

        public Fixture operation(String method, String path) {
            this.method = method;
            this.path = path;
            return this;
        }

        public Fixture details(String details) {
            this.details = details;
            return this;
        }

        public Fixture body(String body) {
            this.body = body;
            return this;
        }

        public Fixture confidence(FindingConfidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Fixture template(String id, String text) {
            this.templateId = id;
            this.template = text;
            return this;
        }

        public Fixture bodyFields(String... names) {
            List.of(names).forEach(name -> fields.add(RequestTarget.body(name)));
            return this;
        }

        public Fixture queryField(String name) {
            fields.add(RequestTarget.query(name));
            return this;
        }

        public Fixture sameAsBaseline(String testId) {
            this.baselineTest = testId;
            return this;
        }

        public CatsTestCaseSummary build() {
            CatsTestCase testCase = new CatsTestCase();
            testCase.setTestId("Test " + number);
            testCase.setResult(result);
            testCase.setResultReason(reason);
            testCase.setResultDetails(details);
            testCase.setFuzzer(fuzzer);
            testCase.setContractPath(path);
            testCase.setConfidence(confidence);
            testCase.setSameErrorAsBaselineTest(baselineTest);
            testCase.setSameErrorAsBaselineFuzzer(baselineTest == null ? null : "HappyPath");
            testCase.setRequest(CatsRequest.builder().httpMethod(method).url("http://localhost" + path).payload("{}").build());
            testCase.setResponse(CatsResponse.builder().responseCode(status).body(body).build());
            testCase.getRequestProvenance().addMutationTargets(fields);
            CatsTestCaseSummary summary = CatsTestCaseSummary.fromCatsTestCase(testCase);
            if (templateId != null) {
                summary.assignErrorTemplate(templateId, template);
            }
            return summary;
        }
    }
}
