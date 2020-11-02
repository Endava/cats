package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ContractInfoFuzzer
@Component
@Slf4j
public class RecommendedHeadersContractInfoFuzzer extends BaseContractInfoFuzzer {
    static final List<String> HEADERS = Arrays.asList("correlationid", "traceid");

    @Autowired
    public RecommendedHeadersContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path contains recommended headers such as CorrelationId/TraceId");
        testCaseListener.addExpectedResult(log, "Recommended headers [TraceId/CorrelationId] must be present");

        List<CatsHeader> recommendedHeaders = data.getHeaders().stream()
                .filter(catsHeader -> HEADERS.parallelStream().anyMatch(this.replaceSpecialChars(catsHeader.getName()).toLowerCase()::contains))
                .collect(Collectors.toList());

        if (recommendedHeaders.isEmpty()) {
            testCaseListener.reportError(log, "Path does not contain the recommended [TracedId/CorrelationId] headers for HTTP method {}", data.getMethod());
        } else {
            testCaseListener.reportInfo(log, "Path contains the recommended [TracedId/CorrelationId] headers for HTTP method {}", data.getMethod());
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
