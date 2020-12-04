package com.endava.cats.io;

import com.endava.cats.CatsMain;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import com.google.common.html.HtmlEscapers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
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
import org.springframework.util.MimeTypeUtils;

import javax.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

import static com.endava.cats.util.CustomFuzzerUtil.ADDITIONAL_PROPERTIES;

/**
 * This class is responsible for the HTTP interaction with the target server supplied in the {@code --server} parameter
 */
@Component
public class ServiceCaller {
    public static final String CATS_REMOVE_FIELD = "cats_remove_field";
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceCaller.class);
    private static final List<String> AUTH_HEADERS = Arrays.asList("authorization", "jwt", "api-key", "api_key", "apikey",
            "secret", "secret-key", "secret_key", "api-secret", "api_secret", "apisecret", "api-token", "api_token", "apitoken");
    private final CatsParams catsParams;
    private final CatsUtil catsUtil;
    private final TestCaseListener testCaseListener;
    private final CatsDSLParser catsDSLParser;
    private HttpClient httpClient;
    @Value("${proxyHost:empty}")
    private String proxyHost;
    @Value("${proxyPort:0}")
    private int proxyPort;
    @Value("${server:empty}")
    private String server;

    @Autowired
    public ServiceCaller(TestCaseListener lr, CatsUtil cu, CatsParams catsParams, CatsDSLParser cdsl) {
        this.testCaseListener = lr;
        this.catsUtil = cu;
        this.catsParams = catsParams;
        this.catsDSLParser = cdsl;
    }

    @PostConstruct
    public void initHttpClient() {
        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, (certificate, authType) -> true).build();
            HostnameVerifier hostnameVerifier = new NoopHostnameVerifier();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslSocketFactory).build();
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setMaxTotal(100);
            connectionManager.setDefaultMaxPerRoute(100);
            HttpHost httpHost = null;
            if (!"empty".equalsIgnoreCase(proxyHost)) {
                LOGGER.info("Proxy configuration to be used: host={}, port={}", proxyHost, proxyPort);
                httpHost = new HttpHost(proxyHost, proxyPort);
            }
            httpClient = HttpClients.custom().setConnectionManager(connectionManager).setSSLContext(sslContext).setProxy(httpHost).build();
        } catch (GeneralSecurityException e) {
            LOGGER.warn("Failed to configure HTTP CLIENT socket factory");
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
     * Parameters in the URL will be replaced with actual values supplied in the path.properties and catsParams.getRefData().yml files
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
        for (String line : catsParams.getUrlParamsList()) {
            String[] urlParam = line.split(":");
            String pathVar = "{" + urlParam[0] + "}";
            if (startingUrl.contains(pathVar)) {
                startingUrl = startingUrl.replace("{" + urlParam[0] + "}", urlParam[1]);
            }
        }
        return startingUrl;
    }


    private String replaceRemovedParams(String path) {
        return path.replaceAll("\\{(.*?)}", "");
    }


    private CatsResponse createAndExecute(ServiceData data, HttpRequestBase method) {
        String processedPayload = this.replacePayloadWithRefData(data);

        try {
            this.addMandatoryHeaders(data, method);
            this.addSuppliedHeaders(method, data.getRelativePath(), data);
            this.setHttpMethodPayload(method, processedPayload, data);
            this.removeSkippedHeaders(data, method);

            LOGGER.info("Final list of request headers: {}", Arrays.asList(method.getAllHeaders()));
            LOGGER.info("Final payload: {}", processedPayload);
            long startTime = System.currentTimeMillis();
            HttpResponse response = httpClient.execute(method);
            long endTime = System.currentTimeMillis();

            LOGGER.info("Protocol: {}, Method: {}, ReasonPhrase: {}, ResponseCode: {}, ResponseTimeInMs: {}", response.getStatusLine().getProtocolVersion(),
                    method.getMethod(), response.getStatusLine().getReasonPhrase(), response.getStatusLine().getStatusCode(), endTime - startTime);

            String responseBody = this.getAsJson(response);
            List<CatsHeader> responseHeaders = Arrays.stream(response.getAllHeaders()).map(header -> CatsHeader.builder().name(header.getName()).value(header.getValue()).build()).collect(Collectors.toList());

            CatsResponse catsResponse = CatsResponse.from(response.getStatusLine().getStatusCode(), responseBody, method.getMethod(), endTime - startTime, responseHeaders, data.getFuzzedFields());
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
        if (processedPath.contains("{")) {
            for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
                if (child.getValue().isJsonNull()) {
                    processedPath = processedPath.replaceAll("\\{" + child.getKey() + "}", "");
                } else {
                    processedPath = processedPath.replaceAll("\\{" + child.getKey() + "}", getEncodedUrl(child.getValue().getAsString()));
                }
                data.getPathParams().add(child.getKey());
            }
        }
        return processedPath;
    }

    private String getEncodedUrl(String path) {
        return path.replace(" ", "%20");
    }

    private void addMandatoryHeaders(ServiceData data, HttpRequestBase method) {
        data.getHeaders().forEach(header -> method.addHeader(header.getName(), header.getValue()));
        addIfNotPresent("Accept", data, method);
        addIfNotPresent("Content-Type", data, method);
    }

    private void addIfNotPresent(String header, ServiceData data, HttpRequestBase method) {
        boolean notAccept = data.getHeaders().stream().noneMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(header));
        if (notAccept) {
            method.addHeader(header, MimeTypeUtils.APPLICATION_JSON_VALUE);
        }
    }

    private List<NameValuePair> buildQueryParameters(String payload, ServiceData data) {
        List<NameValuePair> queryParams = new ArrayList<>();
        JsonElement jsonElement = catsUtil.parseAsJsonElement(payload);
        for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
            if (!data.getPathParams().contains(child.getKey()) || data.getQueryParams().contains(child.getKey())) {
                if (child.getValue().isJsonNull()) {
                    queryParams.add(new BasicNameValuePair(child.getKey(), null));
                } else if (child.getValue().isJsonArray()) {
                    queryParams.add(new BasicNameValuePair(child.getKey(), child.getValue().toString().replace("[", "")
                            .replace("]", "").replace("\"", "")));
                } else {
                    queryParams.add(new BasicNameValuePair(child.getKey(), child.getValue().getAsString()));
                }
            }
        }
        return queryParams;
    }

    private String getAsJson(HttpResponse response) throws IOException {
        String responseAsString = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : null;

        if (StringUtils.isBlank(responseAsString)) {
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
        LOGGER.info("Path {} has the following headers: {}", relativePath, catsParams.getHeaders().get(relativePath));
        LOGGER.info("Headers that should be added to all paths: {}", catsParams.getHeaders().get(CatsMain.ALL));

        Map<String, String> suppliedHeaders = catsParams.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(relativePath) || entry.getKey().equalsIgnoreCase(CatsMain.ALL))
                .map(Map.Entry::getValue).collect(HashMap::new, Map::putAll, Map::putAll);

        for (Map.Entry<String, String> suppliedHeader : suppliedHeaders.entrySet()) {
            if (data.isAddUserHeaders()) {
                this.replaceHeaderIfNotFuzzed(method, data, suppliedHeader);
            } else if (!data.isAddUserHeaders() && (this.isSuppliedHeaderInFuzzData(data, suppliedHeader) || this.isAuthenticationHeader(suppliedHeader.getKey()))) {
                method.setHeader(suppliedHeader.getKey(), suppliedHeader.getValue());
            }
        }
    }

    private boolean isSuppliedHeaderInFuzzData(ServiceData data, Map.Entry<String, String> suppliedHeader) {
        return data.getHeaders().stream().anyMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(suppliedHeader.getKey()));
    }

    private boolean isAuthenticationHeader(String header) {
        return AUTH_HEADERS.stream().anyMatch(authHeader -> authHeader.equalsIgnoreCase(header));
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
     * @return the path with reference data replacing path parameters
     */
    private String replacePathWithRefData(ServiceData data, String currentUrl) {
        Map<String, String> currentPathRefData = catsParams.getRefData(data.getRelativePath());
        LOGGER.info("Path reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), currentPathRefData);

        for (Map.Entry<String, String> entry : currentPathRefData.entrySet()) {
            currentUrl = currentUrl.replace("{" + entry.getKey() + "}", entry.getValue());
            data.getPathParams().add(entry.getKey());
        }

        return currentUrl;
    }

    String replacePayloadWithRefData(ServiceData data) {
        if (!data.isReplaceRefData()) {
            LOGGER.info("Bypassing reference data replacement for path {}!", data.getRelativePath());
            return data.getPayload();
        } else {
            Map<String, String> refDataForCurrentPath = catsParams.getRefData(data.getRelativePath());
            LOGGER.info("Payload reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), refDataForCurrentPath);
            Map<String, String> refDataWithoutAdditionalProperties = refDataForCurrentPath.entrySet().stream()
                    .filter(stringStringEntry -> !stringStringEntry.getKey().equalsIgnoreCase(ADDITIONAL_PROPERTIES))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            String payload = data.getPayload();

            for (Map.Entry<String, String> entry : refDataWithoutAdditionalProperties.entrySet()) {
                String refDataValue = catsDSLParser.parseAndGetResult(entry.getValue(), data.getPayload());
                FuzzingStrategy fuzzingStrategy = FuzzingStrategy.replace().withData(refDataValue);
                boolean mergeFuzzing = data.getFuzzedFields().contains(entry.getKey());
                try {
                    if (CATS_REMOVE_FIELD.equalsIgnoreCase(refDataValue)) {
                        payload = catsUtil.deleteNode(payload, entry.getKey());
                    } else {
                        payload = catsUtil.replaceField(payload, entry.getKey(), fuzzingStrategy, mergeFuzzing).getJson();
                    }
                } catch (PathNotFoundException e) {
                    LOGGER.warn("Ref data key {} was not found within the payload!", entry.getKey());
                }
            }

            payload = catsUtil.setAdditionalPropertiesToPayload(refDataForCurrentPath, payload);

            LOGGER.info("Final payload after reference data replacement: {}", payload);

            return payload;
        }
    }
}
