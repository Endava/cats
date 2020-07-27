package com.endava.cats.io;

import com.endava.cats.CatsMain;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.common.html.HtmlEscapers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for the HTTP interaction with the target server supplied in the {@code --server} parameter
 */
@Component
public class ServiceCaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceCaller.class);
    private static HttpClient httpClient;

    static {
        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, (certificate, authType) -> true).build();
            HostnameVerifier hostnameVerifier = new NoopHostnameVerifier();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslSocketFactory).build();
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setMaxTotal(100);
            connectionManager.setDefaultMaxPerRoute(100);

            httpClient = HttpClients.custom().setConnectionManager(connectionManager).setSSLContext(sslContext).build();
        } catch (Exception e) {
            LOGGER.warn("Failed to configure HTTP CLIENT socket factory");
        }
    }

    @Value("${server:empty}")
    private String server;
    @Value("${refData:empty}")
    private String refDataFile;
    @Value("${headers:empty}")
    private String headersFile;
    @Value("${urlParams:empty}")
    private String urlParams;

    private CatsUtil catsUtil;
    private TestCaseListener testCaseListener;

    private Map<String, Map<String, String>> headers = new HashMap<>();
    private Map<String, Map<String, String>> refData = new HashMap<>();
    private List<String> urlParamsList = new ArrayList<>();


    @Autowired
    public ServiceCaller(TestCaseListener lr, CatsUtil cu) {
        this.testCaseListener = lr;
        this.catsUtil = cu;
    }

    @PostConstruct
    public void loadURLParams() {
        if (CatsMain.EMPTY.equalsIgnoreCase(urlParams)) {
            LOGGER.info("No URL parameters supplied!");
        } else {
            try {
                urlParamsList = Arrays.stream(urlParams.split(",")).map(String::trim).collect(Collectors.toList());

                LOGGER.info("URL parameters: {}", urlParamsList);
            } catch (Exception e) {
                LOGGER.error("Processing of URL parameters failed! Exception: {}", e.getMessage());
            }
        }
    }


    @PostConstruct
    public void loadRefData() {
        if (CatsMain.EMPTY.equalsIgnoreCase(refDataFile)) {
            LOGGER.info("No reference data file was supplied! Payloads supplied by Fuzzers will remain unchanged!");
        } else {
            try {
                this.mapObjsToString(refDataFile, refData);

                LOGGER.info("Reference data file loaded successfully: {}", refData);
            } catch (Exception e) {
                LOGGER.error("Processing of reference data file failed! Exception: {}", e.getMessage());
            }
        }
    }

    @PostConstruct
    public void loadHeaders() {
        if (CatsMain.EMPTY.equalsIgnoreCase(headersFile)) {
            LOGGER.info("No headers file was supplied! No additional header will be added!");
        } else {
            try {
                this.mapObjsToString(headersFile, headers);
                LOGGER.info("Headers file loaded: {}", headers);
            } catch (Exception e) {
                LOGGER.error("Processing of headers file failed! Exception: {}", e.getMessage());
            }
        }
    }

    private void mapObjsToString(String headersFile, Map<String, Map<String, String>> headers) throws IOException {
        Map<String, Map<String, Object>> headersAsObject = catsUtil.parseYaml(headersFile);
        for (Map.Entry<String, Map<String, Object>> entry : headersAsObject.entrySet()) {
            headers.put(entry.getKey(), entry.getValue().entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
        }
    }

    public CatsResponse call(HttpMethod method, ServiceData data) {
        switch (method) {
            case POST:
                return this.post(data);
            case PUT:
                return this.put(data);
            case GET:
                return this.get(data);
            case TRACE:
                return this.trace(data);
            case DELETE:
                return this.delete(data);
            case PATCH:
                return this.patch(data);
            case HEAD:
                return this.head(data);
        }
        return null;
    }

    public CatsResponse post(ServiceData serviceData) {
        HttpPost post = new HttpPost(this.getPathWithRefDataReplacedForHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, post);
    }

    public CatsResponse get(ServiceData serviceData) {
        HttpGet get = new HttpGet(this.getPathWithRefDataReplacedForNonHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, get);
    }

    public CatsResponse delete(ServiceData serviceData) {
        HttpDelete delete = new HttpDelete(this.getPathWithRefDataReplacedForNonHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, delete);
    }

    public CatsResponse put(ServiceData serviceData) {
        HttpPut put = new HttpPut(this.getPathWithRefDataReplacedForHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, put);
    }

    public CatsResponse patch(ServiceData serviceData) {
        HttpPatch patch = new HttpPatch(this.getPathWithRefDataReplacedForHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, patch);
    }

    public CatsResponse head(ServiceData serviceData) {
        HttpHead head = new HttpHead(this.getPathWithRefDataReplacedForNonHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, head);
    }

    public CatsResponse trace(ServiceData serviceData) {
        HttpTrace trace = new HttpTrace(this.getPathWithRefDataReplacedForNonHttpEntityRequests(serviceData, server + serviceData.getRelativePath()));
        return createAndExecute(serviceData, trace);
    }

    /**
     * Parameters in the URL will be replaced with actual values supplied in the path.properties and refData.yml files
     *
     * @param data
     * @param startingUrl
     * @return
     */
    private String getPathWithRefDataReplacedForNonHttpEntityRequests(ServiceData data, String startingUrl) {
        String actualUrl = this.getPathWithSuppliedURLParamsReplaced(startingUrl);

        if (StringUtils.isNotEmpty(data.getPayload())) {
            String processedPayload = this.replacePayloadWithRefData(data);
            actualUrl = this.replacePathParams(actualUrl, processedPayload, data);
            actualUrl = this.replaceRemovedParams(actualUrl);
        } else {
            actualUrl = this.replacePathWithRefData(data, actualUrl);
        }

        return actualUrl;
    }

    private String getPathWithRefDataReplacedForHttpEntityRequests(ServiceData data, String startingUrl) {
        String actualUrl = this.getPathWithSuppliedURLParamsReplaced(startingUrl);
        return this.replacePathWithRefData(data, actualUrl);
    }


    /**
     * Parameters in the URL will be replaced with actual values supplied in the {@code --urlParams} parameter
     *
     * @param startingUrl
     * @return
     */
    private String getPathWithSuppliedURLParamsReplaced(String startingUrl) {
        for (String line : urlParamsList) {
            String[] split = line.split(":");
            String pathVar = "{" + split[0] + "}";
            if (startingUrl.contains(pathVar)) {
                startingUrl = startingUrl.replaceAll("\\{" + split[0] + "}", split[1]);
            }
        }
        return startingUrl;
    }


    private String replaceRemovedParams(String path) {
        return path.replaceAll("\\{(.*?)\\}", "");
    }


    private CatsResponse createAndExecute(ServiceData data, HttpRequestBase method) {
        String processedPayload = this.replacePayloadWithRefData(data);

        try {
            this.addMandatoryHeaders(data, method);
            this.addSuppliedHeaders(method, data.getRelativePath(), data);
            this.setHttpMethodPayload(method, processedPayload, data);
            this.removeSkippedHeaders(data, method);

            LOGGER.info("Final list with request headers {}", Arrays.asList(method.getAllHeaders()));

            HttpResponse response = httpClient.execute(method);
            LOGGER.info("Protocol: {}, Method: {}, ReasonPhrase: {}, ResponseCode: {}", response.getStatusLine().getProtocolVersion(),
                    method.getMethod(), response.getStatusLine().getReasonPhrase(), response.getStatusLine().getStatusCode());

            String responseBody = this.getAsJson(response);

            CatsResponse catsResponse = CatsResponse.from(response.getStatusLine().getStatusCode(), responseBody, method.getMethod());
            this.recordRequestAndResponse(Arrays.asList(method.getAllHeaders()), processedPayload, catsResponse, data.getRelativePath(), HtmlEscapers.htmlEscaper().escape(method.getURI().toString()));

            return catsResponse;
        } catch (IOException | URISyntaxException e) {
            this.recordRequestAndResponse(Arrays.asList(method.getAllHeaders()), processedPayload, CatsResponse.empty(), data.getRelativePath(), HtmlEscapers.htmlEscaper().escape(method.getURI().toString()));
            throw new CatsIOException(e);
        }
    }

    private void removeSkippedHeaders(ServiceData data, HttpRequestBase method) {
        for (String skippedHeader : data.getSkippedHeaders()) {
            method.removeHeaders(skippedHeader);
        }
    }

    private void setHttpMethodPayload(HttpRequestBase method, String processedPayload, ServiceData data) throws UnsupportedEncodingException, URISyntaxException {
        if (method instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) method).setEntity(new StringEntity(processedPayload));
        } else if (StringUtils.isNotEmpty(processedPayload) && !"null".equalsIgnoreCase(processedPayload)) {
            URIBuilder builder = new URIBuilder(method.getURI());
            builder.addParameters(this.buildQueryParameters(processedPayload, data));
            method.setURI(builder.build());
        }
    }

    private String replacePathParams(String path, String processedPayload, ServiceData data) {
        JsonElement jsonElement = catsUtil.parseAsJsonElement(processedPayload);

        String processedPath = path;
        for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
            if (child.getValue().isJsonNull()) {
                processedPath = processedPath.replaceAll("\\{" + child.getKey() + "}", "");
            } else {
                processedPath = processedPath.replaceAll("\\{" + child.getKey() + "}", getEncodedUrl(child.getValue().getAsString()));
            }
            data.getPathParams().add(child.getKey());
        }

        return processedPath;
    }

    private String getEncodedUrl(String path) {
        return path.replace(" ", "%20");
    }

    private void addMandatoryHeaders(ServiceData data, HttpRequestBase method) {
        data.getHeaders().forEach(header -> method.addHeader(header.getName(), header.getValue()));
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-type", "application/json");
    }

    private List<NameValuePair> buildQueryParameters(String payload, ServiceData data) {
        List<NameValuePair> queryParams = new ArrayList<>();
        JsonElement jsonElement = catsUtil.parseAsJsonElement(payload);
        for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
            if (!data.getPathParams().contains(child.getKey())) {
                if (child.getValue().isJsonNull()) {
                    queryParams.add(new BasicNameValuePair(child.getKey(), null));
                } else {
                    queryParams.add(new BasicNameValuePair(child.getKey(), child.getValue().getAsString()));
                }
            }
        }
        return queryParams;
    }

    private String getAsJson(HttpResponse response) throws IOException {
        String responseAsString = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : null;
        if (StringUtils.isEmpty(responseAsString) || StringUtils.isEmpty(responseAsString.trim())) {
            return "";
        } else if (catsUtil.isValidJson(responseAsString)) {
            return responseAsString;
        }
        return "{\"exception\":\"Received response is not a JSON\"}";
    }

    private void recordRequestAndResponse(List<Header> headers, String processedPayload, CatsResponse catsResponse, String relativePath, String fullRequestPath) {
        CatsRequest request = new CatsRequest();
        request.setHeaders(headers);
        request.setPayload(processedPayload);
        testCaseListener.addPath(relativePath);
        testCaseListener.addRequest(request);
        testCaseListener.addResponse(catsResponse);
        testCaseListener.addFullRequestPath(fullRequestPath);
    }

    private void addSuppliedHeaders(HttpRequestBase method, String relativePath, ServiceData data) {
        if (data.isAddUserHeaders()) {
            LOGGER.info("Path {} has the following headers: {}", relativePath, headers.get(relativePath));
            LOGGER.info("Headers that should be added to all paths: {}", headers.get(CatsMain.ALL));

            /* We will only replace headers that were not fuzzed */
            for (Map.Entry<String, String> suppliedHeader : headers.getOrDefault(CatsMain.ALL, Collections.emptyMap()).entrySet()) {
                this.replaceHeaderIfNotFuzzed(method, data, suppliedHeader);
            }

            for (Map.Entry<String, String> suppliedHeader : headers.getOrDefault(relativePath, Collections.emptyMap()).entrySet()) {
                this.replaceHeaderIfNotFuzzed(method, data, suppliedHeader);
            }
        } else {
            for (Map.Entry<String, String> suppliedHeader : headers.getOrDefault(CatsMain.ALL, Collections.emptyMap()).entrySet()) {
                if (isSecurityHeader(suppliedHeader.getKey())) {
                    this.replaceHeaderIfNotFuzzed(method, data, suppliedHeader);
                }
            }
            LOGGER.info("Only security headers will be added. All other custom headers will be ignored!");
        }
    }

    private boolean isSecurityHeader(String header) {
        return header.toLowerCase().contains("authorization") || header.toLowerCase().contains("jwt");
    }

    private void replaceHeaderIfNotFuzzed(HttpRequestBase method, ServiceData data, Map.Entry<String, String> suppliedHeader) {
        if (!data.getFuzzedHeaders().contains(suppliedHeader.getKey())) {
            method.setHeader(suppliedHeader.getKey(), suppliedHeader.getValue());
        } else {
            /* There are 2 cases when we want to mix the supplied header with the fuzzed one: if the fuzzing is TRAIL or PREFIX we want to try these behaviour on a valid header value */
            String finalHeaderValue = FuzzingStrategy.mergeFuzzing(method.getFirstHeader(suppliedHeader.getKey()).getValue(), suppliedHeader.getValue(), "    ");
            method.setHeader(suppliedHeader.getKey(), finalHeaderValue);
            LOGGER.info("Header's [{}] fuzzing will merge with the supplied header value from headers.yml. Final header value {}", suppliedHeader.getKey(), finalHeaderValue);
        }
    }

    /**
     * This method will replace the path parameters with reference data values, without any fuzzing around the values
     *
     * @param data
     * @param currentUrl
     * @return
     */
    private String replacePathWithRefData(ServiceData data, String currentUrl) {
        LOGGER.info("Path reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), refData.get(data.getRelativePath()));

        if (refData.get(data.getRelativePath()) != null) {
            for (Map.Entry<String, String> entry : refData.get(data.getRelativePath()).entrySet()) {
                currentUrl = currentUrl.replaceAll("\\{" + entry.getKey() + "}", entry.getValue());
                data.getPathParams().add(entry.getKey());
            }
        }

        return currentUrl;
    }

    private String replacePayloadWithRefData(ServiceData data) {
        if (!data.isReplaceRefData()) {
            LOGGER.info("Bypassing ref data replacement for path {}!", data.getRelativePath());
        } else {
            LOGGER.info("Payload reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), refData.get(data.getRelativePath()));
            JsonElement jsonElement = catsUtil.parseAsJsonElement(data.getPayload());

            if (jsonElement.isJsonObject()) {
                replaceFieldsWithFuzzedValueIfNotRefData(refData.get(data.getRelativePath()), jsonElement, data.getFuzzedFields());
            } else if (jsonElement.isJsonArray()) {
                for (JsonElement element : jsonElement.getAsJsonArray()) {
                    replaceFieldsWithFuzzedValueIfNotRefData(refData.get(data.getRelativePath()), element, data.getFuzzedFields());
                }
            }
            LOGGER.info("Final payload after reference data replacement {}", jsonElement);

            return jsonElement.toString();

        }
        return data.getPayload();
    }


    private void replaceFieldsWithFuzzedValueIfNotRefData(Map<String, String> refDataForCurrentPath, JsonElement jsonElement, Set<String> fuzzedFields) {
        if (refDataForCurrentPath != null) {
            for (Map.Entry<String, String> entry : refDataForCurrentPath.entrySet()) {
                /*If we didn't fuzz a Ref Data field, we replace the value with the ref data*/
                this.replaceElementWithRefDataValue(entry, jsonElement, fuzzedFields);
            }
        }
    }

    private void replaceElementWithRefDataValue(Map.Entry<String, String> entry, JsonElement jsonElement, Set<String> fuzzedFields) {
        JsonElement element = catsUtil.getJsonElementBasedOnFullyQualifiedName(jsonElement, entry.getKey());
        String[] depth = entry.getKey().split("#");

        if (element != null) {
            String key = depth[depth.length - 1];
            String fuzzedValue = null;

            if (!fuzzedFields.contains(entry.getKey())) {
                fuzzedValue = entry.getValue();
            }

            /* If we fuzzed a Ref Data field, we need to try and merge the fuzzing */
            if (fuzzedFields.contains(entry.getKey()) && !element.getAsJsonObject().get(key).isJsonNull()) {
                fuzzedValue = FuzzingStrategy.mergeFuzzing(element.getAsJsonObject().get(key).getAsString(), entry.getValue(), "   ");
            }
            if (fuzzedValue != null && element.getAsJsonObject().remove(key) != null) {
                element.getAsJsonObject().addProperty(key, fuzzedValue);
                LOGGER.info("Replacing property {} with value {}", entry.getKey(), fuzzedValue);
            }
        }
    }
}
