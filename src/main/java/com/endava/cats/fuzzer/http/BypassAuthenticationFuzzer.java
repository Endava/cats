package com.endava.cats.fuzzer.http;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class BypassAuthenticationFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BypassAuthenticationFuzzer.class);
    private static final String AUTHENTICATION_HEADER = "authorization";
    private static final String JWT = "jwt";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsParams catsParams;

    @Autowired
    public BypassAuthenticationFuzzer(ServiceCaller sc, TestCaseListener lr, CatsParams catsParams) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsParams = catsParams;
    }


    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(LOGGER, "Scenario: send a happy flow bypassing authentication");
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should get a 403 or 401 response code");
        Set<String> authenticationHeaders = this.getAuthenticationHeaderProvided(data);
        if (!authenticationHeaders.isEmpty()) {
            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                    .payload(data.getPayload()).skippedHeaders(authenticationHeaders).queryParams(data.getQueryParams()).build();

            CatsResponse response = serviceCaller.call(data.getMethod(), serviceData);
            testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.FOURXX);
        } else {
            testCaseListener.skipTest(LOGGER, "No authentication header provided.");
        }
    }

    private Set<String> getAuthenticationHeaderProvided(FuzzingData data) {
        Set<String> authenticationHeadersInContract = data.getHeaders().stream().filter(header -> header.getName().toLowerCase().contains(AUTHENTICATION_HEADER) || header.getName().toLowerCase().contains(JWT))
                .map(CatsHeader::getName).collect(Collectors.toSet());
        Set<String> authenticationHeadersInFile = catsParams.getHeaders().entrySet().stream().filter(path -> CatsMain.ALL.equalsIgnoreCase(path.getKey()) || data.getPath().equalsIgnoreCase(path.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.toList())
                .stream().flatMap(entry -> entry.keySet().stream())
                .collect(Collectors.toSet())
                .stream().filter(headerName -> headerName.toLowerCase().contains(AUTHENTICATION_HEADER) || headerName.toLowerCase().contains(JWT))
                .collect(Collectors.toSet());

        return Stream.of(authenticationHeadersInContract, authenticationHeadersInFile).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "check if an authentication header is supplied; if yes try to make requests without it";
    }
}
