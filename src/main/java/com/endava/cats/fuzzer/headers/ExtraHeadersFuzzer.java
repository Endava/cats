package com.endava.cats.fuzzer.headers;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

import static com.endava.cats.dsl.CatsDSLWords.CATS_FUZZY_HEADER;

@Singleton
@HeaderFuzzer
public class ExtraHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ExtraHeadersFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    public ExtraHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(logger, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        Set<CatsHeader> headerSet = new HashSet<>(data.getHeaders());
        headerSet.add(CatsHeader.builder().name(CATS_FUZZY_HEADER).required(false).value(CATS_FUZZY_HEADER).build());

        testCaseListener.addScenario(logger, "Add extra header inside the request: name [{}], value [{}]. All other details are similar to a happy flow", CATS_FUZZY_HEADER, CATS_FUZZY_HEADER);
        testCaseListener.addExpectedResult(logger, "Should get a 2XX response code");

        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).httpMethod(data.getMethod())
                .headers(headerSet).payload(data.getPayload()).queryParams(data.getQueryParams()).contentType(data.getFirstRequestContentType()).build());

        testCaseListener.reportResult(logger, data, response, ResponseCodeFamily.TWOXX);
    }


    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "send a 'happy' flow request and add an extra field inside the request called 'Cats-Fuzzy-Header'";
    }
}