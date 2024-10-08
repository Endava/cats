package com.endava.cats.io;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.util.FormEncoder;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.WordUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.endava.cats.util.CatsDSLWords.ADDITIONAL_PROPERTIES;
import static com.endava.cats.util.JsonUtils.NOT_SET;

/**
 * This class is responsible for the HTTP interaction with the target server supplied in the {@code --server} parameter
 */
@ApplicationScoped
@SuppressWarnings("UnstableApiUsage")
public class ServiceCaller {
    /**
     * Marker for fields to be removed before calling the service.
     */
    public static final String CATS_REMOVE_FIELD = "cats_remove_field";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ServiceCaller.class);
    private static final List<String> AUTH_HEADERS = Arrays.asList("authorization", "jwt", "api-key", "api_key", "apikey",
            "secret", "secret-key", "secret_key", "api-secret", "api_secret", "apisecret", "api-token", "api_token", "apitoken");
    private final FilesArguments filesArguments;
    private final TestCaseListener testCaseListener;
    private final AuthArguments authArguments;
    private final ApiArguments apiArguments;
    private final ProcessingArguments processingArguments;
    private final CatsGlobalContext catsGlobalContext;
    OkHttpClient okHttpClient;

    private RateLimiter rateLimiter;

    /**
     * Constructs a new {@code ServiceCaller} with the specified parameters.
     *
     * @param context             The global context for CATS.
     * @param lr                  The listener for test cases.
     * @param filesArguments      The arguments related to files.
     * @param authArguments       The authentication arguments.
     * @param apiArguments        The API arguments.
     * @param processingArguments The processing arguments.
     */
    @Inject
    public ServiceCaller(CatsGlobalContext context, TestCaseListener lr, FilesArguments filesArguments, AuthArguments authArguments, ApiArguments apiArguments, ProcessingArguments processingArguments) {
        this.testCaseListener = lr;
        this.filesArguments = filesArguments;
        this.authArguments = authArguments;
        this.apiArguments = apiArguments;
        this.processingArguments = processingArguments;
        this.catsGlobalContext = context;
    }

    /**
     * Inits the rate limiter with the value received in the {@code --maxRequestsPerMinute} argument.
     */
    @PostConstruct
    public void initRateLimiter() {
        rateLimiter = RateLimiter.create(1.0 * apiArguments.getMaxRequestsPerMinute() / 60);
    }

    /**
     * Inits the OkHttpClient with the configuration passed through the CLI arguments.
     */
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
                    .retryOnConnectionFailure(true)
                    .hostnameVerifier((hostname, session) -> true).build();

            logger.debug("Proxy configuration to be used: {}", authArguments.getProxy());
        } catch (GeneralSecurityException | IOException e) {
            logger.warning("Failed to configure HTTP CLIENT: {}", e.getMessage());
            logger.debug("Stacktrace", e);
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
        this.recordServiceData(data);

        String processedPayload = this.replacePayloadWithRefData(data);
        processedPayload = this.convertPayloadInSpecificContentType(processedPayload, data);
        logger.debug("Payload replaced with ref data: {}", processedPayload);

        List<KeyValuePair<String, Object>> headers = this.buildHeaders(data);
        CatsRequest catsRequest = CatsRequest.builder()
                .headers(headers).payload(processedPayload)
                .httpMethod(data.getHttpMethod().name())
                .build();

        long startTime = System.currentTimeMillis();
        try {
            String url = this.constructUrl(data, processedPayload);

            catsRequest.setUrl(url);
            this.recordRequest(catsRequest);

            logger.note("Final list of request headers: {}", headers);
            logger.note("Final payload: {}", processedPayload);
            logger.note("Final url: {}", url);

            startTime = System.currentTimeMillis();
            CatsResponse response = this.callService(catsRequest, data.getFuzzedFields());

            this.recordResponse(response);
            return response;
        } catch (IOException | IllegalStateException e) {
            long duration = System.currentTimeMillis() - startTime;

            CatsResponse.ExceptionalResponse exceptionalResponse = CatsResponse.getResponseByException(e);

            CatsResponse catsResponse = CatsResponse.builder()
                    .body(exceptionalResponse.responseBody()).httpMethod(catsRequest.getHttpMethod())
                    .responseTimeInMs(duration).responseCode(exceptionalResponse.responseCode())
                    .jsonBody(JsonUtils.parseAsJsonElement(exceptionalResponse.responseBody()))
                    .fuzzedField(data.getFuzzedFields()
                            .stream().findAny().map(el -> el.substring(el.lastIndexOf("#") + 1)).orElse(null))
                    .build();

            this.recordRequestAndResponse(catsRequest, catsResponse, data);

            logger.debug("Stacktrace from ServiceCaller", e);


            return catsResponse;
        }
    }

    /**
     * Final url is being constructed by replacing path variables with the supplied urlParams or refData.
     * It also adds supplied query params if any.
     *
     * @param data             the service data context
     * @param processedPayload current payload
     * @return an url with path params replaced by urlParams or refData + additional query params
     */
    String constructUrl(ServiceData data, String processedPayload) {
        String decodedUrl = CatsUtil.unescapeCurlyBrackets(apiArguments.getServer() + data.getRelativePath());
        if (!data.isReplaceUrlParams()) {
            String actualUrl = this.replacePathParams(decodedUrl, processedPayload, data);
            return this.replaceRemovedParams(actualUrl);
        }

        String url = this.getPathWithRefDataReplacedForHttpEntityRequests(data, apiArguments.getServer() + data.getRelativePath());

        if (!HttpMethod.requiresBody(data.getHttpMethod())) {
            url = this.getPathWithRefDataReplacedForNonHttpEntityRequests(data, apiArguments.getServer() + data.getRelativePath());
            url = this.addUriParams(processedPayload, data, url);
        }
        url = this.addPathParamsIfNotReplaced(url, data.getPathParamsPayload());
        url = this.addAdditionalQueryParams(url, data.getRelativePath());
        return url;
    }

    String addPathParamsIfNotReplaced(String url, String pathParamsPayload) {
        logger.debug("Using the following path params payload {} for path {}", pathParamsPayload, url);

        Set<String> pathVariables = OpenApiUtils.getPathVariables(url);
        logger.debug("Path variables found in the URL: {}", pathVariables);

        for (String pathVariable : pathVariables) {
            String pathValue = String.valueOf(JsonUtils.getVariableFromJson(pathParamsPayload, pathVariable));
            url = url.replace("{" + pathVariable + "}", pathValue);
        }
        return url;
    }

    String addAdditionalQueryParams(String startingUrl, String currentPath) {
        HttpUrl.Builder httpUrl = HttpUrl.get(startingUrl).newBuilder();

        for (Map.Entry<String, Object> queryParamEntry : filesArguments.getAdditionalQueryParamsForPath(currentPath).entrySet()) {
            httpUrl.addQueryParameter(queryParamEntry.getKey(), String.valueOf(queryParamEntry.getValue()));
        }

        return httpUrl.build().toString();
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
            logger.warn("There was a problem converting the payload to the content-type: {}", e.getMessage());
            logger.debug("Stacktrace:", e);
        }
        return payload;
    }

    Map<String, String> getPathParamFromCorrespondingPostIfDelete(ServiceData data) {
        if (data.getHttpMethod() == HttpMethod.DELETE) {
            String postPath = data.getRelativePath().substring(0, data.getRelativePath().lastIndexOf("/"));
            logger.note("Executing DELETE for path {}. Searching stored POST requests for corresponding POST path {}", data.getRelativePath(), postPath);
            String postPayload = catsGlobalContext.getPostSuccessfulResponses().getOrDefault(postPath, new ArrayDeque<>()).peek();
            if (postPayload != null) {
                String deleteParam = data.getRelativePath().substring(data.getRelativePath().lastIndexOf("/") + 1).replace("{", "").replace("}", "");
                logger.note("Found corresponding POST payload. Matching DELETE path parameter {} with POST body...", deleteParam);
                Optional<String> deleteParamValue = this.getParamValueFromPostPayload(deleteParam, postPayload);

                if (deleteParamValue.isPresent()) {
                    return Map.of(deleteParam, deleteParamValue.get());
                }
            } else {
                logger.note("No corresponding POST payload found or already consumed");
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

        logger.debug("Params to search in POST payload {}", candidates);

        for (String candidate : candidates) {
            String value = String.valueOf(JsonUtils.getVariableFromJson(postPayload, candidate));

            if (!value.equalsIgnoreCase(NOT_SET)) {
                logger.note("Found matching DELETE parameter in POST payload using key [{}]", candidate);
                return Optional.of(value);
            }
        }
        logger.warn("Unable to correlate DELETE parameter {} with POST payload", deleteParam);

        return Optional.empty();
    }


    List<KeyValuePair<String, Object>> buildHeaders(ServiceData data) {
        List<KeyValuePair<String, Object>> headers = new ArrayList<>();

        this.addMandatoryHeaders(data, headers);
        this.addSuppliedHeaders(data, headers);
        this.removeSkippedHeaders(data, headers);
        this.addBasicAuth(headers);

        return Collections.unmodifiableList(headers);
    }

    private String addUriParams(String processedPayload, ServiceData data, String currentUrl) {
        if (StringUtils.isNotEmpty(processedPayload) && !"null".equalsIgnoreCase(processedPayload)) {
            HttpUrl.Builder httpUrl = HttpUrl.get(currentUrl).newBuilder();
            List<KeyValuePair<String, String>> queryParams = this.buildQueryParameters(processedPayload, data);
            for (KeyValuePair<String, String> param : queryParams) {
                httpUrl.addQueryParameter(param.getKey(), param.getValue());
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

    /**
     * Calls the service with the provided {@code catsRequest} and set of fuzzed fields.
     *
     * @param catsRequest  The CATS request to be sent to the service.
     * @param fuzzedFields The set of fuzzed fields for the request.
     * @return The CATS response received from the service.
     * @throws IOException If an I/O error occurs during the service call.
     */
    public CatsResponse callService(CatsRequest catsRequest, Set<String> fuzzedFields) throws IOException {
        rateLimiter.acquire();
        long startTime = System.currentTimeMillis();
        RequestBody requestBody = null;
        Headers.Builder headers = new Headers.Builder();
        catsRequest.getHeaders().forEach(header -> headers.addUnsafeNonAscii(header.getKey(), String.valueOf(header.getValue())));

        if (HttpMethod.requiresBody(catsRequest.getHttpMethod())) {
            requestBody = RequestBody.create(catsRequest.getPayload().getBytes(StandardCharsets.UTF_8));
        } else {
            //for GET and HEAD we remove Content-Type as some servers don't like it
            headers.removeAll("Content-Type");
        }

        try (Response response = okHttpClient.newCall(new Request.Builder()
                .url(catsRequest.getUrl())
                .headers(headers.build())
                .method(catsRequest.getHttpMethod(), requestBody)
                .build()).execute()) {
            long endTime = System.currentTimeMillis();

            CatsResponse.CatsResponseBuilder catsResponseBuilder = this.populateCatsResponseFromHttpResponse(response);
            CatsResponse catsResponse = catsResponseBuilder.httpMethod(catsRequest.getHttpMethod())
                    .responseTimeInMs(endTime - startTime)
                    .path(catsRequest.getUrl())
                    .fuzzedField(fuzzedFields.stream().findAny().map(el -> el.substring(el.lastIndexOf("#") + 1)).orElse(null))
                    .build();

            logger.complete("Protocol: {}, Method: {}, ResponseCode: {}, ResponseTimeInMs: {}, ResponseLength: {}, ResponseWords: {}, ResponseLines: {}",
                    response.protocol(), catsResponse.getHttpMethod(), catsResponse.responseCodeAsString(), endTime - startTime,
                    catsResponse.getContentLengthInBytes(), catsResponse.getNumberOfWordsInResponse(), catsResponse.getNumberOfLinesInResponse());

            return catsResponse;
        }
    }

    private CatsResponse.CatsResponseBuilder populateCatsResponseFromHttpResponse(Response response) throws IOException {
        List<KeyValuePair<String, String>> responseHeaders = response.headers()
                .toMultimap()
                .entrySet().stream()
                .map(header -> new KeyValuePair<>(header.getKey(), header.getValue().getFirst())).toList();

        String rawResponse = this.getAsRawString(response);
        String jsonResponse = JsonUtils.getAsJsonString(rawResponse);
        String responseContentType = this.getResponseContentType(response);

        int numberOfWords = new StringTokenizer(rawResponse).countTokens();
        int numberOfLines = rawResponse.split("[\r|\n]").length;

        logger.debug("Raw response body: {}", rawResponse);
        logger.debug("Raw response headers: {}", response.headers());

        return CatsResponse.builder()
                .responseCode(response.code())
                .headers(responseHeaders)
                .body(rawResponse)
                .jsonBody(JsonParser.parseString(jsonResponse))
                .numberOfLinesInResponse(numberOfLines)
                .contentLengthInBytes(rawResponse.getBytes(StandardCharsets.UTF_8).length)
                .responseContentType(responseContentType)
                .numberOfWordsInResponse(numberOfWords);
    }

    private String getResponseContentType(Response response) {
        MediaType defaultResponseMediaType = MediaType.parse(CatsResponse.unknownContentType());
        if (response.body() != null) {
            return String.valueOf(Optional.ofNullable(response.body().contentType()).orElse(defaultResponseMediaType));
        }
        return String.valueOf(defaultResponseMediaType);
    }

    private void addBasicAuth(List<KeyValuePair<String, Object>> headers) {
        if (authArguments.isBasicAuthSupplied()) {
            headers.add(new KeyValuePair<>("Authorization", authArguments.getBasicAuthHeader()));
        }
    }

    private void removeSkippedHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers) {
        for (String skippedHeader : data.getSkippedHeaders()) {
            headers.removeIf(header -> header.getKey().equalsIgnoreCase(skippedHeader));
        }
    }


    private String replacePathParams(String path, String processedPayload, ServiceData data) {
        String payloadAsJson = JsonUtils.parseOrConvertToJsonElement(processedPayload).toString();

        return Arrays.stream(OpenApiUtils.getPathElements(path))
                .filter(pathElement -> pathElement.contains("{"))
                .reduce(path,
                        (currentPath, pathElement) -> {
                            String pathElementWithoutBrackets = pathElement.replace("{", "").replace("}", "");
                            Object pathElementValue = JsonUtils.getVariableFromJson(payloadAsJson, pathElementWithoutBrackets);
                            data.getPathParams().add(pathElementWithoutBrackets);
                            return currentPath.replace(pathElement, URLEncoder.encode(String.valueOf(pathElementValue), StandardCharsets.UTF_8));
                        });
    }

    private void addMandatoryHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers) {
        data.getHeaders().forEach(header -> headers.add(new KeyValuePair<>(header.getName(), header.getValue())));
        addIfNotPresent(HttpHeaders.ACCEPT, processingArguments.getDefaultContentType(), data, headers);
        addIfNotPresent(HttpHeaders.CONTENT_TYPE, this.getContentType(data.getHttpMethod(), data.getContentType()), data, headers);
        addIfNotPresent(HttpHeaders.USER_AGENT, apiArguments.getUserAgent(testCaseListener.getCurrentTestCaseNumber(), testCaseListener.getCurrentFuzzer()), data, headers);
    }

    private String getContentType(HttpMethod method, String defaultContentType) {
        return method == HttpMethod.PATCH && processingArguments.isRfc7396() ? JsonUtils.JSON_PATCH : defaultContentType;
    }

    private void addIfNotPresent(String headerName, String headerValue, ServiceData data, List<KeyValuePair<String, Object>> headers) {
        boolean exists = data.getHeaders().stream().noneMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(headerName));
        if (exists) {
            headers.add(new KeyValuePair<>(headerName, headerValue));
        }
    }

    private List<KeyValuePair<String, String>> buildQueryParameters(String payload, ServiceData data) {
        List<KeyValuePair<String, String>> queryParams = new ArrayList<>();
        JsonElement jsonElement = JsonUtils.parseOrConvertToJsonElement(payload);

        for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
            if (child.getValue().isJsonObject()) {
                queryParams.addAll(this.buildQueryParameters(child.getValue().toString(), data));
            } else if (!data.getPathParams().contains(child.getKey()) || data.getQueryParams().contains(child.getKey()) || CatsDSLWords.isExtraField(child.getKey())) {
                if (child.getValue().isJsonNull()) {
                    logger.debug("Not adding null query parameter {}", child.getKey());
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

    /**
     * Retrieves the raw response body as a string from the provided HTTP response.
     * If the response body is null, an empty string is returned.
     *
     * @param response The HTTP response containing the body to be extracted.
     * @return The raw response body as a string, or an empty string if the body is null.
     * @throws IOException If an I/O error occurs while reading the response body.
     */
    public String getAsRawString(Response response) throws IOException {
        if (response.body() != null) {
            return response.body().string();
        }

        return "";
    }

    private void recordServiceData(ServiceData serviceData) {
        testCaseListener.addPath(serviceData.getContractPath());
        testCaseListener.addContractPath(serviceData.getContractPath());
        testCaseListener.addServer(apiArguments.getServer());
    }

    private void recordRequest(CatsRequest catsRequest) {
        testCaseListener.addRequest(catsRequest);
        testCaseListener.addFullRequestPath(catsRequest.getUrl());
    }

    private void recordResponse(CatsResponse catsResponse) {
        testCaseListener.addResponse(catsResponse);
    }

    private void recordRequestAndResponse(CatsRequest catsRequest, CatsResponse catsResponse, ServiceData serviceData) {
        this.recordServiceData(serviceData);
        this.recordRequest(catsRequest);
        this.recordResponse(catsResponse);
    }

    private void addSuppliedHeaders(ServiceData data, List<KeyValuePair<String, Object>> headers) {
        Map<String, Object> userSuppliedHeaders = filesArguments.getHeaders(data.getContractPath());
        logger.debug("Path {} (including ALL headers) has the following headers: {}", data.getContractPath(), userSuppliedHeaders);

        Map<String, String> suppliedHeaders = userSuppliedHeaders.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> CatsDSLParser.parseAndGetResult(String.valueOf(entry.getValue()), authArguments.getAuthScriptAsMap())));

        for (Map.Entry<String, String> suppliedHeader : suppliedHeaders.entrySet()) {
            if (data.isAddUserHeaders()) {
                this.replaceHeaderIfNotFuzzed(headers, data, suppliedHeader);
            } else if (this.isSuppliedHeaderInFuzzData(data, suppliedHeader) || this.isAuthenticationHeader(suppliedHeader.getKey())) {
                replaceHeaderWithUserSuppliedHeader(headers, suppliedHeader.getKey(), suppliedHeader.getValue());
            }
        }
    }

    private static void replaceHeaderWithUserSuppliedHeader(List<KeyValuePair<String, Object>> headers, String headerName, Object headerValue) {
        /* We need to make sure we add the same number of headers back as this is important for some Fuzzers*/
        Predicate<KeyValuePair<String, Object>> headersToFilter = header -> header.getKey().equalsIgnoreCase(headerName);
        long howManyHeadersToRemove = Math.max(headers.stream().filter(headersToFilter).count(), 1);
        headers.removeIf(headersToFilter);

        LongStream.range(0, howManyHeadersToRemove)
                .forEach(iteration -> headers.add(new KeyValuePair<>(headerName, headerValue)));
    }

    private boolean isSuppliedHeaderInFuzzData(ServiceData data, Map.Entry<String, String> suppliedHeader) {
        return data.getHeaders().stream().anyMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(suppliedHeader.getKey()));
    }

    /**
     * Checks if the given header is an authentication header.
     *
     * @param header the header name
     * @return true if the header is an authentication header, false otherwise
     */
    public boolean isAuthenticationHeader(String header) {
        return AUTH_HEADERS.stream().anyMatch(authHeader -> header.toLowerCase(Locale.ROOT).contains(authHeader));
    }

    private void replaceHeaderIfNotFuzzed(List<KeyValuePair<String, Object>> headers, ServiceData data, Map.Entry<String, String> suppliedHeader) {
        if (!data.getFuzzedHeaders().contains(suppliedHeader.getKey())) {
            replaceHeaderWithUserSuppliedHeader(headers, suppliedHeader.getKey(), suppliedHeader.getValue());
        } else {
            /* There are 2 cases when we want to mix the supplied header with the fuzzed one: if the fuzzing is TRAIL or PREFIX we want to try this behaviour on a valid header value */
            KeyValuePair<String, Object> existingHeader = headers.stream()
                    .filter(header -> header.getKey().equalsIgnoreCase(suppliedHeader.getKey()))
                    .findFirst()
                    .orElse(new KeyValuePair<>("", ""));

            Object finalHeaderValue = FuzzingStrategy.mergeFuzzing(existingHeader.getValue(), suppliedHeader.getValue());
            replaceHeaderWithUserSuppliedHeader(headers, suppliedHeader.getKey(), finalHeaderValue);
            logger.debug("Header's [{}] fuzzing will merge with the supplied header value from headers.yml. Final header value {}", suppliedHeader.getKey(), finalHeaderValue);
        }
    }

    private String replacePathWithRefData(ServiceData data, String currentUrl) {
        Map<String, Object> currentPathRefData = filesArguments.getRefData(data.getRelativePath());
        logger.debug("Path reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), currentPathRefData);

        for (Map.Entry<String, Object> entry : currentPathRefData.entrySet()) {
            currentUrl = currentUrl.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
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
        if (!data.isReplaceRefData() || "null".equals(data.getPayload())) {
            logger.note("Bypassing reference data replacement for path {}!", data.getRelativePath());
            return data.getPayload();
        } else {
            Map<String, Object> refDataForCurrentPath = filesArguments.getRefData(data.getRelativePath());
            logger.debug("Payload reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), refDataForCurrentPath);

            Map<String, Object> refDataWithoutAdditionalProperties = refDataForCurrentPath.entrySet()
                    .stream()
                    .filter(stringStringEntry -> !stringStringEntry.getKey().matches(ADDITIONAL_PROPERTIES))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            String payload = data.getPayload();

            /*this will override refData for DELETE requests in order to provide valid entities that will get deleted*/
            refDataWithoutAdditionalProperties.putAll(this.getPathParamFromCorrespondingPostIfDelete(data));

            for (Map.Entry<String, Object> entry : refDataWithoutAdditionalProperties.entrySet()) {
                Object refDataValue = entry.getValue();
                if (refDataValue instanceof String str) {
                    refDataValue = CatsDSLParser.parseAndGetResult(str, Map.of(Parser.REQUEST, data.getPayload()));
                }
                try {
                    if (CATS_REMOVE_FIELD.equalsIgnoreCase(String.valueOf(refDataValue))) {
                        payload = JsonUtils.deleteNode(payload, entry.getKey());
                    } else {
                        FuzzingStrategy fuzzingStrategy = FuzzingStrategy.replace().withData(refDataValue);
                        boolean mergeFuzzing = data.getFuzzedFields().contains(entry.getKey());
                        payload = FuzzingStrategy.replaceField(payload, entry.getKey(), fuzzingStrategy, mergeFuzzing).json();
                    }
                } catch (PathNotFoundException e) {
                    logger.debug("Ref data key {} was not found within the payload!", entry.getKey());
                }
            }

            payload = CatsUtil.setAdditionalPropertiesToPayload(refDataForCurrentPath, payload);

            logger.debug("Final payload after reference data replacement: {}", payload);

            return payload;
        }
    }
}
