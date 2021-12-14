package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
@HeaderFuzzer
public class ExtraHeaderFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(ExtraHeaderFuzzer.class);
    private static final String CATS_FUZZY_HEADER = "Cats-Fuzzy-Header";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    public ExtraHeaderFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        Set<CatsHeader> headerSet = new HashSet<>(data.getHeaders());
        headerSet.add(CatsHeader.builder().name(CATS_FUZZY_HEADER).required(false).value(CATS_FUZZY_HEADER).build());

        testCaseListener.addScenario(LOGGER, "Add extra header inside the request: name [{}], value [{}]. All other details are similar to a happy flow", CATS_FUZZY_HEADER, CATS_FUZZY_HEADER);
        testCaseListener.addExpectedResult(LOGGER, "Should get a 2XX response code");

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).httpMethod(data.getMethod())
                .headers(headerSet).payload(data.getPayload()).queryParams(data.getQueryParams()).build());

        testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.TWOXX);
    }


    public String toString() {
        return this.getClass().getSimpleName().replace("_Subclass", "");
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and add an extra field inside the request called 'Cats-Fuzzy-Header'";
    }
}