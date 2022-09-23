package com.endava.cats.fuzzer.headers;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.generator.Cloner;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;

@Singleton
@HeaderFuzzer
public class UserDictionaryHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(UserDictionaryHeadersFuzzer.class);
    private final TestCaseListener testCaseListener;
    private final UserArguments userArguments;
    private final MatchArguments matchArguments;
    private final ServiceCaller serviceCaller;

    @Inject
    public UserDictionaryHeadersFuzzer(TestCaseListener testCaseListener, UserArguments userArguments, MatchArguments matchArguments, ServiceCaller sc) {
        this.testCaseListener = testCaseListener;
        this.userArguments = userArguments;
        this.matchArguments = matchArguments;
        this.serviceCaller = sc;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (userArguments.getWords() == null) {
            logger.error("Skipping fuzzer as --words was not provided!");
        } else if (!matchArguments.isAnyMatchArgumentSupplied()) {
            logger.error("Skipping fuzzer as no --m* argument was provided!");
        } else {
            Collection<CatsHeader> headers = Cloner.cloneMe(data.getHeaders());

            for (CatsHeader header : headers) {
                String previousValue = header.getValue();

                try {
                    for (String fuzzValue : userArguments.getWordsAsList()) {
                        header.withValue(fuzzValue);
                        testCaseListener.createAndExecuteTest(logger, this, () -> {
                            testCaseListener.addScenario(logger, "Iterate through each header and send values from user dictionary. Header [{}] with [{}]", header.getName(), header.getValue());
                            testCaseListener.addExpectedResult(logger, "Should return a valid response");

                            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(headers)
                                    .payload(data.getPayload()).fuzzedHeader(header.getName()).queryParams(data.getQueryParams()).httpMethod(data.getMethod())
                                    .contentType(data.getFirstRequestContentType()).build();

                            CatsResponse response = serviceCaller.call(serviceData);
                            if (matchArguments.isMatchResponse(response)) {
                                testCaseListener.reportError(logger, "Service call completed. Please check response details");
                            } else {
                                testCaseListener.skipTest(logger, "Skipping test as response does not match given matchers!");
                            }
                        });
                    }
                } finally {
                    header.withValue(previousValue);
                }
            }
        }
    }

    @Override
    public String description() {
        return "iterates through each request headers and sends values from the user supplied dictionary";
    }

    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
