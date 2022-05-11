package com.endava.cats.io;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.CatsDSLWords;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.WordUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.PathNotFoundException;
import com.stripe.net.FormEncoder;
import com.stripe.net.KeyValuePair;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import net.minidev.json.JSONValue;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.endava.cats.dsl.CatsDSLWords.ADDITIONAL_PROPERTIES;
import static com.endava.cats.model.util.JsonUtils.NOT_SET;

/**
 * This class is responsible for the HTTP interaction with the target server supplied in the {@code --server} parameter
 */
@ApplicationScoped
@SuppressWarnings("UnstableApiUsage")
public class ServiceCaller {
    public static final String CATS_REMOVE_FIELD = "cats_remove_field";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(ServiceCaller.class);
    private static final List<String> AUTH_HEADERS = Arrays.asList("authorization", "jwt", "api-key", "api_key", "apikey",
            "secret", "secret-key", "secret_key", "api-secret", "api_secret", "apisecret", "api-token", "api_token", "apitoken");
    private final FilesArguments filesArguments;
    private final CatsUtil catsUtil;
    private final TestCaseListener testCaseListener;
    private final CatsDSLParser catsDSLParser;
    private final AuthArguments authArguments;
    private final ApiArguments apiArguments;
    private final ProcessingArguments processingArguments;
    private final CatsGlobalContext catsGlobalContext;
    OkHttpClient okHttpClient;

    private RateLimiter rateLimiter;

    @Inject
    public ServiceCaller(CatsGlobalContext context, TestCaseListener lr, CatsUtil cu, FilesArguments filesArguments, CatsDSLParser cdsl, AuthArguments authArguments, ApiArguments apiArguments, ProcessingArguments processingArguments) {
        this.testCaseListener = lr;
        this.catsUtil = cu;
        this.filesArguments = filesArguments;
        this.catsDSLParser = cdsl;
        this.authArguments = authArguments;
        this.apiArguments = apiArguments;
        this.processingArguments = processingArguments;
        this.catsGlobalContext = context;
    }

    @PostConstruct
    public void initRateLimiter() {
        rateLimiter = RateLimiter.create(1.0 * apiArguments.getMaxRequestsPerMinute() / 60);
    }

    @PostConstruct
    public void initHttpClient() {
        try {
            final TrustManager[] trustAllCerts = this.buildTrustAllManager();
            final SSLSocketFactory sslSocketFactory = this.buildSslSocketFactory(trustAllCerts);

            okHttpClient = new OkHttpClient.Builder()
                    .proxy(authArguments.getProxy())
                    .connectTimeout(apiArguments.getConnectionTimeout(), TimeUnit.SECONDS)
                    .readTimeout(apiArguments.getReadTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(apiArguments.getWriteTimeout(), TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(10, 15, TimeUnit.MINUTES))
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .connectionPool(new ConnectionPool())
                    .hostnameVerifier((hostname, session) -> true).build();

            LOGGER.note("Proxy configuration to be used: {}", authArguments.getProxy());
        } catch (GeneralSecurityException | IOException e) {
            LOGGER.warning("Failed to configure HTTP CLIENT", e);
        }
    }

    private TrustManager[] buildTrustAllManager() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        //we don't do anything here
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        //we don't do anything here
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
    }

    private SSLSocketFactory buildSslSocketFactory(TrustManager[] trustAllCerts) throws IOException, GeneralSecurityException {
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");

        if (authArguments.isMutualTls()) {
            try (InputStream inputStream = new FileInputStream(authArguments.getSslKeystore())) {
                KeyStore keyStore = KeyStore.getInstance("jks");
                keyStore.load(inputStream, authArguments.getSslKeystorePwd().toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, authArguments.getSslKeystorePwd().toCharArray());
                sslContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, new SecureRandom());
            }
        } else {
            sslContext.init(null, trustAllCerts, new SecureRandom());
        }

        return sslContext.getSocketFactory();
    }

    /**
     * When in dryRun mode ServiceCaller won't do any actual calls.
     *
     * @param data the current context data
     * @return the result of service invocation
     */
    @DryRun
    public CatsResponse call(ServiceData data) {
        String processedPayload = this.replacePayloadWithRefData(data);
        processedPayload = this.convertPayloadInSpecificContentType(processedPayload, data);
        LOGGER.debug("Payload replaced with ref data: {}", processedPayload);

        List<CatsRequest.Header> headers = this.buildHeaders(data);
        CatsRequest catsRequest = CatsRequest.builder()
                .headers(headers).payload(processedPayload)
                .httpMethod(data.getHttpMethod().name())
                .build();

        try {
            String url = this.getPathWithRefDataReplacedForHttpEntityRequests(data, apiArguments.getServer() + data.getRelativePath());

            if (!HttpMethod.requiresBody(data.getHttpMethod())) {
                url = this.getPathWithRefDataReplacedForNonHttpEntityRequests(data, apiArguments.getServer() + data.getRelativePath());
                url = this.addUriParams(processedPayload, data, url);
            }
            catsRequest.setUrl(url);

            LOGGER.note("Final list of request headers: {}", headers);
            LOGGER.note("Final payload: {}", processedPayload);
            LOGGER.note("Final url: {}", url);

            CatsResponse response = this.callService(catsRequest, data.getFuzzedFields());

            this.recordRequestAndResponse(catsRequest, response, data);
            return response;
        } catch (IOException e) {
            this.recordRequestAndResponse(catsRequest, CatsResponse.empty(), data);
            throw new CatsIOException(e);
        }
    }

    String convertPayloadInSpecificContentType(String payload, ServiceData data) {
        try {
            if (data.isJsonContentType() || StringUtils.isBlank(payload)) {
                return payload;
            }
            HashMap<String, Object> payloadAsMap = new ObjectMapper().readValue(payload, new TypeReference<>() {
            });
            return FormEncoder.createHttpContent(payloadAsMap).stringContent();
        } catch (IOException e) {
            LOGGER.warn("There was a problem converting the payload to the content-type: {}", e.getMessage());
            LOGGER.debug("Stacktrace:", e);
        }
        return payload;
    }

    Map<String, String> getPathParamFromCorrespondingPostIfDelete(ServiceData data) {
        if (data.getHttpMethod() == HttpMethod.DELETE) {
            String postPath = data.getRelativePath().substring(0, data.getRelativePath().lastIndexOf("/"));
            LOGGER.info("Executing DELETE for path {}. Searching stored POST requests for corresponding POST path {}", data.getRelativePath(), postPath);
            String postPayload = catsGlobalContext.getPostSuccessfulResponses().getOrDefault(postPath, new ArrayDeque<>()).peek();
            if (postPayload != null) {
                String deleteParam = data.getRelativePath().substring(data.getRelativePath().lastIndexOf("/") + 1).replace("{", "").replace("}", "");
                LOGGER.info("Found corresponding POST payload. Matching DELETE path parameter {} with POST body...", deleteParam);
                Optional<String> deleteParamValue = this.getParamValueFromPostPayload(deleteParam, postPayload);

                if (deleteParamValue.isPresent()) {
                    return Map.of(deleteParam, deleteParamValue.get());
                }
            } else {
                LOGGER.info("No corresponding POST payload found or already consumed");
            }
        }

        return Collections.emptyMap();
    }

    Optional<String> getParamValueFromPostPayload(String deleteParam, String postPayload) {
        String[] camelCase = deleteParam.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        String[] snakeCase = deleteParam.split("_");
        String[] kebabCase = deleteParam.split("-");

        Set<String> candidates = new TreeSet<>();
        candidates.addAll(WordUtils.createWordCombinations(snakeCase));
        candidates.addAll(WordUtils.createWordCombinations(camelCase));
        candidates.addAll(WordUtils.createWordCombinations(kebabCase));

        LOGGER.info("Params to search in POST payload {}", candidates);

        for (String candidate : candidates) {
            String value = String.valueOf(JsonUtils.getVariableFromJson(postPayload, candidate));

            if (!value.equalsIgnoreCase(NOT_SET)) {
                LOGGER.note("Found matching DELETE parameter in POST payload using key [{}]", candidate);
                return Optional.of(value);
            }
        }
        LOGGER.warn("Unable to correlate DELETE parameter {} with POST payload", deleteParam);

        return Optional.empty();
    }


    List<CatsRequest.Header> buildHeaders(ServiceData data) {
        List<CatsRequest.Header> headers = new ArrayList<>();

        this.addMandatoryHeaders(data, headers);
        this.addSuppliedHeaders(headers, data.getRelativePath(), data);
        this.removeSkippedHeaders(data, headers);
        this.addBasicAuth(headers);

        return headers;
    }

    private String addUriParams(String processedPayload, ServiceData data, String currentUrl) {
        if (StringUtils.isNotEmpty(processedPayload) && !"null".equalsIgnoreCase(processedPayload)) {
            HttpUrl.Builder httpUrl = HttpUrl.get(currentUrl).newBuilder();
            List<KeyValuePair<String, String>> queryParams = this.buildQueryParameters(processedPayload, data);
            for (KeyValuePair<String, String> param : queryParams) {
                httpUrl.addEncodedQueryParameter(param.getKey(), param.getValue());
            }
            return httpUrl.build().toString();
        }

        return currentUrl;
    }

    /**
     * Parameters in the URL will be replaced with actual values supplied in the {@code --urlParams} and {@code filesArguments.getRefData()} file.
     *
     * @param data        the service data
     * @param startingUrl initial url constructed from contract
     * @return the URL with variables replaced based on the supplied values
     */
    private String getPathWithRefDataReplacedForNonHttpEntityRequests(ServiceData data, String startingUrl) {
        String actualUrl = this.filesArguments.replacePathWithUrlParams(startingUrl);

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
        String actualUrl = this.filesArguments.replacePathWithUrlParams(startingUrl);
        return this.replacePathWithRefData(data, actualUrl);
    }

    private String replaceRemovedParams(String path) {
        return path.replaceAll("\\{(.*?)}", "");
    }

    public CatsResponse callService(CatsRequest catsRequest, Set<String> fuzzedFields) throws IOException {
        rateLimiter.acquire();
        long startTime = System.currentTimeMillis();
        RequestBody requestBody = null;
        Headers.Builder headers = new Headers.Builder();
        catsRequest.getHeaders().forEach(header -> headers.addUnsafeNonAscii(header.getName(), String.valueOf(header.getValue())));

        if (HttpMethod.requiresBody(catsRequest.getHttpMethod())) {
            requestBody = RequestBody.create(catsRequest.getPayload().getBytes(StandardCharsets.UTF_8));
        }
        Response response = okHttpClient.newCall(new Request.Builder()
                .url(catsRequest.getUrl())
                .headers(headers.build())
                .method(catsRequest.getHttpMethod(), requestBody)
                .build()).execute();
        long endTime = System.currentTimeMillis();

        CatsResponse.CatsResponseBuilder catsResponseBuilder = this.populateCatsResponseFromHttpResponse(response);
        CatsResponse catsResponse = catsResponseBuilder.httpMethod(catsRequest.getHttpMethod())
                .responseTimeInMs(endTime - startTime)
                .fuzzedField(fuzzedFields.stream().findAny().map(el -> el.substring(el.lastIndexOf("#") + 1)).orElse(null))
                .build();

        LOGGER.complete("Protocol: {}, Method: {}, ReasonPhrase: {}, ResponseCode: {}, ResponseTimeInMs: {}, ResponseLength: {}", response.protocol(),
                catsResponse.getHttpMethod(), response.message(), catsResponse.responseCodeAsString(), endTime - startTime, catsResponse.getContentLengthInBytes());

        return catsResponse;
    }

    private CatsResponse.CatsResponseBuilder populateCatsResponseFromHttpResponse(Response response) throws IOException {
        List<CatsHeader> responseHeaders = response.headers()
                .toMultimap()
                .entrySet().stream()
                .map(header -> CatsHeader.builder().name(header.getKey()).value(header.getValue().get(0)).build()).collect(Collectors.toList());

        String rawResponse = this.getAsRawString(response);
        String jsonResponse = this.getAsJsonString(rawResponse);
        int numberOfWords = new StringTokenizer(rawResponse).countTokens();
        int numberOfLines = rawResponse.split("[\r|\n]").length;

        LOGGER.debug("Raw response body: {}", rawResponse);
        LOGGER.debug("Raw response headers: {}", response.headers());

        return CatsResponse.builder()
                .responseCode(response.code())
                .headers(responseHeaders)
                .body(rawResponse)
                .jsonBody(JsonParser.parseString(jsonResponse))
                .numberOfLinesInResponse(numberOfLines)
                .contentLengthInBytes(rawResponse.getBytes(StandardCharsets.UTF_8).length)
                .numberOfWordsInResponse(numberOfWords);
    }

    private void addBasicAuth(List<CatsRequest.Header> headers) {
        if (authArguments.isBasicAuthSupplied()) {
            headers.add(new CatsRequest.Header("Authorization", authArguments.getBasicAuthHeader()));
        }
    }

    private void removeSkippedHeaders(ServiceData data, List<CatsRequest.Header> headers) {
        for (String skippedHeader : data.getSkippedHeaders()) {
            headers.removeIf(header -> header.getName().equalsIgnoreCase(skippedHeader));
        }
    }


    private String replacePathParams(String path, String processedPayload, ServiceData data) {
        JsonElement jsonElement = JsonUtils.parseAsJsonElement(processedPayload);

        String processedPath = path;
        if (processedPath.contains("{")) {
            for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
                String toReplaceWith = "";
                if (child.getValue().isJsonNull()) {
                    toReplaceWith = "";
                } else if (child.getValue().isJsonArray()) {
                    toReplaceWith = URLEncoder.encode(child.getValue().getAsJsonArray().get(0).getAsString(), StandardCharsets.UTF_8);
                } else {
                    toReplaceWith = URLEncoder.encode(child.getValue().getAsString(), StandardCharsets.UTF_8);
                }
                processedPath = processedPath.replaceAll("\\{" + child.getKey() + "}", toReplaceWith);
                data.getPathParams().add(child.getKey());
            }
        }
        return processedPath;
    }

    private void addMandatoryHeaders(ServiceData data, List<CatsRequest.Header> headers) {
        data.getHeaders().forEach(header -> headers.add(new CatsRequest.Header(header.getName(), header.getValue())));
        addIfNotPresent(HttpHeaders.ACCEPT, processingArguments.getDefaultContentType(), data, headers);
        addIfNotPresent(HttpHeaders.CONTENT_TYPE, data.getContentType(), data, headers);
    }

    private void addIfNotPresent(String headerName, String headerValue, ServiceData data, List<CatsRequest.Header> headers) {
        boolean notAccept = data.getHeaders().stream().noneMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(headerName));
        if (notAccept) {
            headers.add(new CatsRequest.Header(headerName, headerValue));
        }
    }

    private List<KeyValuePair<String, String>> buildQueryParameters(String payload, ServiceData data) {
        List<KeyValuePair<String, String>> queryParams = new ArrayList<>();
        JsonElement jsonElement = JsonUtils.parseAsJsonElement(payload);
        for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
            if (!data.getPathParams().contains(child.getKey()) || data.getQueryParams().contains(child.getKey()) || CatsDSLWords.isExtraField(child.getKey())) {
                if (child.getValue().isJsonNull()) {
                    queryParams.add(new KeyValuePair<>(child.getKey(), null));
                } else if (child.getValue().isJsonArray()) {
                    queryParams.add(new KeyValuePair<>(child.getKey(), child.getValue().toString().replace("[", "")
                            .replace("]", "").replace("\"", "")));
                } else {
                    queryParams.add(new KeyValuePair<>(child.getKey(), child.getValue().getAsString()));
                }
            }
        }
        return queryParams;
    }

    public String getAsJsonString(String rawResponse) {
        if (JsonUtils.isValidJson(rawResponse)) {
            return rawResponse;
        }
        return "{\"notAJson\": \"" + JSONValue.escape(rawResponse.substring(0, Math.min(500, rawResponse.length()))) + "\"}";
    }

    public String getAsRawString(Response response) throws IOException {
        if (response.body() != null) {
            return response.body().string();
        }

        return "";
    }

    private void recordRequestAndResponse(CatsRequest catsRequest, CatsResponse catsResponse, ServiceData serviceData) {
        testCaseListener.addPath(serviceData.getRelativePath());
        testCaseListener.addRequest(catsRequest);
        testCaseListener.addResponse(catsResponse);
        testCaseListener.addFullRequestPath(HtmlEscapers.htmlEscaper().escape(catsRequest.getUrl()));
    }

    private void addSuppliedHeaders(List<CatsRequest.Header> headers, String relativePath, ServiceData data) {
        LOGGER.note("Path {} has the following headers: {}", relativePath, filesArguments.getHeaders().get(relativePath));
        LOGGER.note("Headers that should be added to all paths: {}", filesArguments.getHeaders().get(CatsDSLWords.ALL));

        Map<String, String> suppliedHeadersFromFile = filesArguments.getHeaders().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(relativePath) || entry.getKey().equalsIgnoreCase(CatsDSLWords.ALL))
                .map(Map.Entry::getValue).collect(HashMap::new, Map::putAll, Map::putAll);

        Map<String, String> suppliedHeaders = suppliedHeadersFromFile.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> catsDSLParser.parseAndGetResult(entry.getValue(), null)));

        for (Map.Entry<String, String> suppliedHeader : suppliedHeaders.entrySet()) {
            if (data.isAddUserHeaders()) {
                this.replaceHeaderIfNotFuzzed(headers, data, suppliedHeader);
            } else if (!data.isAddUserHeaders() && (this.isSuppliedHeaderInFuzzData(data, suppliedHeader) || this.isAuthenticationHeader(suppliedHeader.getKey()))) {
                headers.removeIf(header -> header.getName().equalsIgnoreCase(suppliedHeader.getKey()));
                headers.add(new CatsRequest.Header(suppliedHeader.getKey(), suppliedHeader.getValue()));
            }
        }
    }

    private boolean isSuppliedHeaderInFuzzData(ServiceData data, Map.Entry<String, String> suppliedHeader) {
        return data.getHeaders().stream().anyMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(suppliedHeader.getKey()));
    }

    public boolean isAuthenticationHeader(String header) {
        return AUTH_HEADERS.stream().anyMatch(authHeader -> header.toLowerCase().contains(authHeader));
    }

    private void replaceHeaderIfNotFuzzed(List<CatsRequest.Header> headers, ServiceData data, Map.Entry<String, String> suppliedHeader) {
        if (!data.getFuzzedHeaders().contains(suppliedHeader.getKey())) {
            headers.removeIf(header -> header.getName().equalsIgnoreCase(suppliedHeader.getKey()));
            headers.add(new CatsRequest.Header(suppliedHeader.getKey(), suppliedHeader.getValue()));
        } else {
            /* There are 2 cases when we want to mix the supplied header with the fuzzed one: if the fuzzing is TRAIL or PREFIX we want to try this behaviour on a valid header value */
            CatsRequest.Header existingHeader = headers.stream().filter(header -> header.getName().equalsIgnoreCase(suppliedHeader.getKey())).findFirst().orElse(new CatsRequest.Header("", ""));
            Object finalHeaderValue = FuzzingStrategy.mergeFuzzing(existingHeader.getValue(), suppliedHeader.getValue());
            headers.removeIf(header -> header.getName().equalsIgnoreCase(suppliedHeader.getKey()));
            headers.add(new CatsRequest.Header(suppliedHeader.getKey(), finalHeaderValue));
            LOGGER.note("Header's [{}] fuzzing will merge with the supplied header value from headers.yml. Final header value {}", suppliedHeader.getKey(), finalHeaderValue);
        }
    }

    private String replacePathWithRefData(ServiceData data, String currentUrl) {
        Map<String, String> currentPathRefData = filesArguments.getRefData(data.getRelativePath());
        LOGGER.note("Path reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), currentPathRefData);

        for (Map.Entry<String, String> entry : currentPathRefData.entrySet()) {
            currentUrl = currentUrl.replace("{" + entry.getKey() + "}", entry.getValue());
            data.getPathParams().add(entry.getKey());
        }

        return currentUrl;
    }

    /**
     * Besides reading data from the {@code --refData} file, this method will aso try to
     * correlate POST recorded data with DELETE endpoints in order to maximize success rate of DELETE requests.
     *
     * @param data the current ServiceData context
     * @return the initial payload with reference data replaced and matching POST correlations for DELETE requests
     */
    String replacePayloadWithRefData(ServiceData data) {
        if (!data.isReplaceRefData()) {
            LOGGER.note("Bypassing reference data replacement for path {}!", data.getRelativePath());
            return data.getPayload();
        } else {
            Map<String, String> refDataForCurrentPath = filesArguments.getRefData(data.getRelativePath());
            LOGGER.note("Payload reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), refDataForCurrentPath);

            Map<String, String> refDataWithoutAdditionalProperties = refDataForCurrentPath.entrySet().stream()
                    .filter(stringStringEntry -> !stringStringEntry.getKey().equalsIgnoreCase(ADDITIONAL_PROPERTIES))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            String payload = data.getPayload();

            /*this will override refData for DELETE requests in order to provide valid entities that will get deleted*/
            refDataWithoutAdditionalProperties.putAll(this.getPathParamFromCorrespondingPostIfDelete(data));

            for (Map.Entry<String, String> entry : refDataWithoutAdditionalProperties.entrySet()) {
                String refDataValue = catsDSLParser.parseAndGetResult(entry.getValue(), data.getPayload());

                try {
                    if (CATS_REMOVE_FIELD.equalsIgnoreCase(refDataValue)) {
                        payload = JsonUtils.deleteNode(payload, entry.getKey());
                    } else {

                        FuzzingStrategy fuzzingStrategy = FuzzingStrategy.replace().withData(refDataValue);
                        boolean mergeFuzzing = data.getFuzzedFields().contains(entry.getKey());
                        payload = catsUtil.replaceField(payload, entry.getKey(), fuzzingStrategy, mergeFuzzing).getJson();
                    }
                } catch (PathNotFoundException e) {
                    LOGGER.warning("Ref data key {} was not found within the payload!", entry.getKey());
                }
            }

            payload = catsUtil.setAdditionalPropertiesToPayload(refDataForCurrentPath, payload);

            LOGGER.note("Final payload after reference data replacement: {}", payload);

            return payload;
        }
    }
}
