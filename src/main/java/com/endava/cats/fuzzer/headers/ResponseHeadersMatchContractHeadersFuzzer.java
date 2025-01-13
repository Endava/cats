package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Fuzzer that checks if response headers match the ones defined in the contract.
 */
@Singleton
@HeaderFuzzer
public class ResponseHeadersMatchContractHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Constructs a new instance of ResponseHeadersMatchContractHeadersFuzzer.
     *
     * <p>This fuzzer is designed to match response headers against contract headers and utilizes a SimpleExecutor
     * for executing fuzzing. The provided TestCaseListener is used for handling test case events.</p>
     *
     * @param testCaseListener The TestCaseListener instance responsible for handling test case events.
     * @param simpleExecutor   The SimpleExecutor responsible for executing fuzzing with response headers matching contract headers.
     */
    @Inject
    public ResponseHeadersMatchContractHeadersFuzzer(TestCaseListener testCaseListener, SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!data.getResponseHeaders().isEmpty()) {
            simpleExecutor.execute(SimpleExecutorContext.builder()
                    .fuzzingData(data)
                    .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                    .fuzzer(this)
                    .payload(data.getPayload())
                    .scenario("Send a 'happy' flow request with all fields and all headers and checks if the response headers match those declared in the contract")
                    .logger(logger)
                    .responseProcessor(this::processResponse)
                    .build());
        }
    }

    private void processResponse(CatsResponse catsResponse, FuzzingData fuzzingData) {
        Set<String> expectedResponseHeaders = Optional.ofNullable(fuzzingData.getResponseHeaders().get(catsResponse.responseCodeAsString()))
                .orElse(Collections.emptySet());

        Set<String> notReturnedHeaders = expectedResponseHeaders.stream()
                .filter(name -> !catsResponse.containsHeader(name))
                .collect(Collectors.toCollection(TreeSet::new));

        if (notReturnedHeaders.isEmpty()) {
            testCaseListener.reportResult(logger, fuzzingData, catsResponse, ResponseCodeFamilyPredefined.TWOXX);
        } else {
            testCaseListener.reportResultError(logger, fuzzingData, CatsResultFactory.Reason.MISSING_RESPONSE_HEADERS.value(),
                    "The following response headers defined in the contract are missing: {}", notReturnedHeaders.toArray());
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a request with all fields and headers populated and checks if the response headers match the ones defined in the contract";
    }
}
