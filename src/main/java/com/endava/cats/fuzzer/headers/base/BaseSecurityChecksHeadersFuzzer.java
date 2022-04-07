package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.generator.Cloner;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Base class used to send different values in Accept and Content-Type headers.
 */
public abstract class BaseSecurityChecksHeadersFuzzer implements Fuzzer {
    protected static final String CATS_ACCEPT = "application/cats";
    private static final List<String> UNSUPPORTED_MEDIA_TYPES = Arrays.asList("application/java-archive",
            "application/javascript",
            "application/octet-stream",
            "application/ogg",
            "application/pdf",
            "application/xhtml+xml",
            "application/x-shockwave-flash",
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
            "multipart/mixed; boundary=cats",
            "multipart/alternative; boundary=cats",
            "multipart/related; boundary=cats",
            "multipart/form-data; boundary=cats",
            "text/css",
            "text/csv",
            "text/html",
            "text/javascript",
            "text/plain",
            "text/xml");
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    protected BaseSecurityChecksHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    /**
     * Removes content types defined in the contract from the {@link UNSUPPORTED_MEDIA_TYPES} list.
     * Content Types not already defined in contract should be treated as invalid by the service.
     *
     * @param data         the current FuzzingData
     * @param headerName   either Accept or Content-Type
     * @param contentTypes Content-Type headers supported by the contract
     * @return a list of set of headers that will be sent in independent requests
     */
    protected static List<Set<CatsHeader>> filterHeaders(FuzzingData data, String headerName, List<String> contentTypes) {
        List<Set<CatsHeader>> setOfSets = new ArrayList<>();

        for (String currentHeader : UNSUPPORTED_MEDIA_TYPES) {
            if (contentTypes.stream().noneMatch(currentHeader::startsWith)) {
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
        testCaseListener.addScenario(log, "Send a happy flow request with a [{}] {} header, value [{}]", typeOfHeader(), targetHeaderName(), headerValue);
        testCaseListener.addExpectedResult(log, "Should get a {} response code", getExpectedResponseCode());
        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(new ArrayList<>(headers))
                .payload(data.getPayload()).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).contentType(data.getFirstRequestContentType()).build());

        testCaseListener.reportResult(log, data, response, ResponseCodeFamily.FOURXX_MT);
    }

    /**
     * What is the expected response code.
     *
     * @return a HTTP expected response code
     */
    public abstract String getExpectedResponseCode();

    /**
     * Short description of the type of data sent within the headers.
     *
     * @return a short description
     */
    public abstract String typeOfHeader();

    /**
     * The name of the targeted header.
     *
     * @return the name of the header
     */
    public abstract String targetHeaderName();

    /**
     * A list of HTTP headers sets that will be used to create test cases. CATS will create a test case for each Set.
     *
     * @param data the current FuzzingData
     * @return a list of header sets
     */
    public abstract List<Set<CatsHeader>> getHeaders(FuzzingData data);

    @Override
    public String toString() {
        return this.getClass().getSimpleName().replace("_Subclass", "");
    }

    @Override
    public String description() {
        return String.format("send a request with a %s %s header and expect to get %s code", typeOfHeader(), targetHeaderName(), getExpectedResponseCode());
    }
}
