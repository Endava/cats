package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@ContractInfoFuzzer
@Singleton
public class RecommendedHeadersContractInfoFuzzer extends BaseContractInfoFuzzer {
    static final List<String> HEADERS = Arrays.asList("correlationid", "traceid");
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public RecommendedHeadersContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path contains recommended headers such as CorrelationId/TraceId for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "Recommended headers [TraceId/CorrelationId] must be present");

        List<CatsHeader> recommendedHeaders = data.getHeaders().stream()
                .filter(catsHeader -> HEADERS.parallelStream().anyMatch(this.replaceSpecialChars(catsHeader.getName()).toLowerCase()::contains)).toList();

        if (recommendedHeaders.isEmpty()) {
            testCaseListener.reportResultError(log, data, "No traceId/correlationId headers", "Path does not contain the recommended [TracedId/CorrelationId] headers for HTTP method {}", data.getMethod());
        } else {
            testCaseListener.reportResultInfo(log, data, "Path contains the recommended [TracedId/CorrelationId] headers for HTTP method {}", data.getMethod());
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    private String replaceSpecialChars(String header) {
        return header.replaceAll("[\\s-_]+", "");
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract paths contain recommended headers like: CorrelationId/TraceId, etc. ";
    }
}
