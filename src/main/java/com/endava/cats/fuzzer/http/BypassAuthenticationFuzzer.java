package com.endava.cats.fuzzer.http;

import com.endava.cats.CatsMain;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HttpFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@HttpFuzzer
public class BypassAuthenticationFuzzer implements Fuzzer {
    private static final List<String> AUTH_HEADERS = Arrays.asList("authorization", "jwt", "api-key", "api_key", "apikey",
            "secret", "secret-key", "secret_key", "api-secret", "api_secret", "apisecret", "api-token", "api_token", "apitoken");
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(BypassAuthenticationFuzzer.class);

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final FilesArguments filesArguments;

    @Autowired
    public BypassAuthenticationFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments filesArguments) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.filesArguments = filesArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(LOGGER, "Send a happy flow bypassing authentication");
        testCaseListener.addExpectedResult(LOGGER, "Should get a 403 or 401 response code");
        Set<String> authenticationHeaders = this.getAuthenticationHeaderProvided(data);
        if (!authenticationHeaders.isEmpty()) {
            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders()).httpMethod(data.getMethod())
                    .payload(data.getPayload()).skippedHeaders(authenticationHeaders).queryParams(data.getQueryParams()).build();

            CatsResponse response = serviceCaller.call(serviceData);
            testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.FOURXX_AA);
        } else {
            testCaseListener.skipTest(LOGGER, "No authentication header provided.");
        }
    }

    protected Set<String> getAuthenticationHeaderProvided(FuzzingData data) {
        Set<String> authenticationHeadersInContract = data.getHeaders().stream().map(CatsHeader::getName)
                .filter(this::isAuthenticationHeader).collect(Collectors.toSet());
        Set<String> authenticationHeadersInFile = filesArguments.getHeaders().entrySet().stream().filter(path -> CatsMain.ALL.equalsIgnoreCase(path.getKey()) || data.getPath().equalsIgnoreCase(path.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.toList())
                .stream().flatMap(entry -> entry.keySet().stream())
                .collect(Collectors.toSet())
                .stream().filter(this::isAuthenticationHeader)
                .collect(Collectors.toSet());

        return Stream.of(authenticationHeadersInContract, authenticationHeadersInFile).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private boolean isAuthenticationHeader(String header) {
        return AUTH_HEADERS.stream().anyMatch(authHeader -> header.toLowerCase().contains(authHeader));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "check if an authentication header is supplied; if yes try to make requests without it";
    }
}
