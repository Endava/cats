package com.endava.cats.fuzzer.executor;

import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import jakarta.inject.Singleton;

import java.util.List;

/** Keeps controlled responses to deliberately unusual inputs separate from API-contract conformance checks. */
@Singleton
public class RobustnessResponseReporter {
    private static final ResponseCodeFamilyDynamic EXPECTED = new ResponseCodeFamilyDynamic(List.of("2XX", "3XX", "4XX"));
    private final TestCaseListener listener;

    public RobustnessResponseReporter(TestCaseListener listener) {
        this.listener = listener;
    }

    public static ResponseCodeFamilyDynamic expectedResponses() {
        return EXPECTED;
    }

    public void report(PrettyLogger logger, FuzzingData data, CatsResponse response) {
        int code = response.getResponseCode();
        if (code >= 200 && code < 500 && !response.isBodyTruncated() && !response.isRateLimited()) {
            listener.reportResultInfo(logger, data, "Controlled HTTP {} response to a parser/decoder robustness probe; no server failure observed.", code);
        } else {
            listener.reportResult(logger, data, response, EXPECTED, false, false);
        }
    }
}
