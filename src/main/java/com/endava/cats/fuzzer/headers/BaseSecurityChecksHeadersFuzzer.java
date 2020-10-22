package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.Cloner;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
public abstract class BaseSecurityChecksHeadersFuzzer implements Fuzzer {
    protected static final List<String> UNSUPPORTED_MEDIA_TYPES = Arrays.asList("application/java-archive",
            "application/javascript",
            "application/octet-stream",
            "application/ogg",
            "application/pdf",
            "application/xhtml+xml",
            "application/x-shockwave-flash",
            "application/json",
            "application/ld+json",
            "application/xml",
            "application/zip",
            "application/x-www-form-urlencoded",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/vnd.microsoft.icon",
            "image/x-icon",
            "image/vnd.djvu",
            "image/svg+xml",
            "multipart/mixed",
            "multipart/alternative",
            "multipart/related",
            "multipart/form-data",
            "text/css",
            "text/csv",
            "text/html",
            "text/javascript",
            "text/plain",
            "text/xml");
    protected static final String CATS_ACCEPT = "application/cats";
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    protected BaseSecurityChecksHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    protected static List<Set<CatsHeader>> filterHeaders(FuzzingData data, String headerName, List<String> contentTypes) {
        List<Set<CatsHeader>> setOfSets = new ArrayList<>();

        for (String currentHeader : UNSUPPORTED_MEDIA_TYPES) {
            if (!contentTypes.contains(currentHeader)) {
                Set<CatsHeader> clonedHeaders = Cloner.cloneMe(data.getHeaders());
                clonedHeaders.add(CatsHeader.builder().name(headerName).value(currentHeader).build());
                setOfSets.add(clonedHeaders);
            }
        }
        return setOfSets;
    }

    public void fuzz(FuzzingData data) {
        for (Set<CatsHeader> headers : this.getHeaders(data)) {
            testCaseListener.createAndExecuteTest(log, this, () -> process(data, headers));
        }
    }

    private void process(FuzzingData data, Set<CatsHeader> headers) {
        String headerValue = headers.stream().filter(header -> header.getName().equalsIgnoreCase(targetHeaderName()))
                .findFirst().orElse(CatsHeader.builder().build()).getValue();
        testCaseListener.addScenario(log, "Scenario: send a flow request with a [{}] {} header, value [{}]", typeOfHeader(), targetHeaderName(), headerValue);
        testCaseListener.addExpectedResult(log, "Expected result: should get a {} response code", getExpectedResponseCode());
        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).headers(new ArrayList<>(headers))
                .payload(data.getPayload()).queryParams(data.getQueryParams()).build());

        testCaseListener.reportResult(log, data, response, ResponseCodeFamily.FOURXX_MT);
    }

    public abstract String getExpectedResponseCode();

    public abstract String typeOfHeader();

    public abstract String targetHeaderName();

    public abstract List<Set<CatsHeader>> getHeaders(FuzzingData data);

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return String.format("send a request with a %s %s header and expect to get %s code", typeOfHeader(), targetHeaderName(), getExpectedResponseCode());
    }
}
