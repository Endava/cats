package com.endava.cats.io;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.auth.wfc.WfcAuthProvider;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.exception.CatsExecutionCancelledException;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.util.FormEncoder;
import com.endava.cats.io.util.HttpContent;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.PayloadFormat;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.QueryParameterSerialization;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.HttpHeaders;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.RateLimiter;
import com.endava.cats.util.SensitiveDataPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.endava.cats.util.CatsDSLWords.ADDITIONAL_PROPERTIES;

/**
 * This class is responsible for the HTTP interaction with the target server supplied in the {@code --server} parameter
 */
@ApplicationScoped
public class ServiceCaller {
    /**
     * Marker for fields to be removed before calling the service.
     */
    public static final String CATS_REMOVE_FIELD = "cats_remove_field";
    private static final Object SUBSTITUTE_FOR_NULL = "SET_TO_NULL";
    private static final String CATS_HEADER_UUID = "X-Cats-Trace-Id";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ServiceCaller.class);
    private final FilesArguments filesArguments;
    private final TestCaseListener testCaseListener;
    private final AuthArguments authArguments;
    private final ApiArguments apiArguments;
    private final ProcessingArguments processingArguments;
    private final ReportingArguments reportingArguments;
    private final CatsGlobalContext catsGlobalContext;
    private final WfcAuthProvider wfcAuthProvider;
    private final RuntimeResourcePool runtimeResourcePool;
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
    public ServiceCaller(CatsGlobalContext context, TestCaseListener lr, FilesArguments filesArguments, AuthArguments authArguments, ApiArguments apiArguments, ProcessingArguments processingArguments,
                         ReportingArguments reportingArguments, WfcAuthProvider wfcAuthProvider, RuntimeResourcePool runtimeResourcePool) {
        this.testCaseListener = lr;
        this.filesArguments = filesArguments;
        this.authArguments = authArguments;
        this.apiArguments = apiArguments;
        this.processingArguments = processingArguments;
        this.reportingArguments = reportingArguments;
        this.catsGlobalContext = context;
        this.wfcAuthProvider = wfcAuthProvider;
        this.runtimeResourcePool = runtimeResourcePool;
    }

    /**
     * Inits the rate limiter with the value received in the {@code --maxRequestsPerMinute} argument.
     */
    @PostConstruct
    public void initRateLimiter() {
        rateLimiter = new RateLimiter(apiArguments.getMaxRequestsPerMinute());
    }

    /**
     * Inits the OkHttpClient with the configuration passed through the CLI arguments.
     */
    @PostConstruct
    public void initHttpClient() {
        okHttpClient = null;
        try {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .proxy(authArguments.getProxy())
                    .connectTimeout(apiArguments.getConnectionTimeout(), TimeUnit.SECONDS)
                    .callTimeout(apiArguments.getCallTimeout(), TimeUnit.SECONDS)
                    .readTimeout(apiArguments.getReadTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(apiArguments.getWriteTimeout(), TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(10, 15, TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .protocols(processingArguments.isHttp2PriorKnowledge() ? List.of(Protocol.H2_PRIOR_KNOWLEDGE) : List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));

            if (authArguments.isMutualTls() || authArguments.isInsecure()) {
                X509TrustManager trustManager = authArguments.isInsecure() ? buildTrustAllManager() : buildDefaultTrustManager();
                clientBuilder.sslSocketFactory(buildSslSocketFactory(trustManager), trustManager);
            }
            if (authArguments.isInsecure()) {
                clientBuilder.hostnameVerifier((_, _) -> true);
            }
            okHttpClient = clientBuilder.build();

            logger.debug("Proxy configuration to be used: {}", authArguments.getProxy());
        } catch (GeneralSecurityException | IOException e) {
            logger.warning("Failed to configure HTTP CLIENT: {}", e.getMessage());
            logger.debug("Stacktrace", e);
        }
    }

    /**
     * Cancels HTTP calls that are active or waiting in the client dispatcher.
     */
    public void cancelActiveCalls() {
        if (okHttpClient != null) {
            okHttpClient.dispatcher().cancelAll();
        }
    }

    private X509TrustManager buildTrustAllManager() {
        return new X509TrustManager() {
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
        };
    }

    private X509TrustManager buildDefaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        return Arrays.stream(trustManagerFactory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new GeneralSecurityException("No default X509 trust manager available"));
    }

    private SSLSocketFactory buildSslSocketFactory(X509TrustManager trustManager) throws IOException, GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManager[] keyManagers = null;

        if (authArguments.isMutualTls()) {
            char[] keystorePassword = Optional.ofNullable(authArguments.getSslKeystorePwd()).orElse("").toCharArray();
            char[] keyPassword = Optional.ofNullable(authArguments.getSslKeyPwd())
                    .orElse(authArguments.getSslKeystorePwd() == null ? "" : authArguments.getSslKeystorePwd())
                    .toCharArray();
            try (InputStream inputStream = new FileInputStream(authArguments.getSslKeystore())) {
                KeyStore keyStore = KeyStore.getInstance("jks");
                keyStore.load(inputStream, keystorePassword);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, keyPassword);
                keyManagers = keyManagerFactory.getKeyManagers();
            }
        }
        sslContext.init(keyManagers, new TrustManager[]{trustManager}, new SecureRandom());
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
        testCaseListener.addMutationTargets(data.getAllMutationTargets());

        String baselinePayload = this.applySuccessfulRequestBaseline(data);
        String processedPayload = this.replacePayloadWithRefData(data, baselinePayload);
        RuntimeResourcePool.ResolvedRequest resolvedRequest = this.enrichWithRuntimeResources(data, processedPayload);
        processedPayload = resolvedRequest.payload();
        resolvedRequest.correlations().forEach(correlation -> testCaseListener.addRuntimeCorrelation(logger, correlation));
        EncodedRequestBody encodedBody = this.encodeRequestBody(processedPayload, data);
        processedPayload = encodedBody.payload();
        logger.debug("Payload replaced with ref data: {}", processedPayload);

        List<KeyValuePair<String, Object>> headers = this.buildHeaders(data, encodedBody.contentType());
        RuntimeResourcePool.ResolvedHeaders resolvedHeaders = this.enrichHeadersWithRuntimeResources(data, headers,
                resolvedRequest);
        headers = resolvedHeaders.headers();
        resolvedHeaders.correlations().forEach(correlation -> testCaseListener.addRuntimeCorrelation(logger, correlation));
        CatsRequest catsRequest = CatsRequest.builder()
                .headers(headers).payload(processedPayload)
                .httpMethod(data.getHttpMethod().name())
                .build();

        long startTime = System.currentTimeMillis();
        try {
            String url = this.constructUrl(data, processedPayload, resolvedRequest.pathParamsPayload());

            catsRequest.setUrl(url);
            this.recordRequest(catsRequest);

            logger.note("Final list of request headers: {}", SensitiveDataPolicy.maskHeadersForDisplay(headers, reportingArguments));
            logger.note("Final payload: {}", processedPayload);
            logger.note("Final url: {}", SensitiveDataPolicy.maskUrl(url, reportingArguments));

            startTime = System.currentTimeMillis();
            CatsResponse response = this.callService(catsRequest, data.getResponseValidationFields());

            this.recordResponse(response);
            this.observeRuntimeResources(data, catsRequest, response, resolvedRequest);
            return response;
        } catch (IOException | IllegalStateException e) {
            this.discardRuntimeCorrelations(resolvedRequest);
            long duration = System.currentTimeMillis() - startTime;

            CatsResponse.ExceptionalResponse exceptionalResponse = CatsResponse.getResponseByException(e);

            CatsResponse catsResponse = CatsResponse.builder()
                    .body(exceptionalResponse.responseBody()).httpMethod(catsRequest.getHttpMethod())
                    .responseTimeInMs(duration).responseCode(exceptionalResponse.responseCode())
                    .declaredContentLength(-1).responseBodyLimit(apiArguments.getMaxResponseBytes())
                    .jsonBody(JsonUtils.parseAsJsonElement(exceptionalResponse.responseBody()))
                    .responseValidationField(data.getResponseValidationFields()
                            .stream().findAny().map(el -> el.substring(el.lastIndexOf("#") + 1)).orElse(null))
                    .build();

            this.recordRequestAndResponse(catsRequest, catsResponse, data);

            logger.debug("Stacktrace from ServiceCaller", e);


            return catsResponse;
        }
    }

    private RuntimeResourcePool.ResolvedHeaders enrichHeadersWithRuntimeResources(
            ServiceData data, List<KeyValuePair<String, Object>> headers,
            RuntimeResourcePool.ResolvedRequest resolvedRequest) {
        try {
            RuntimeResourcePool.ResolvedHeaders resolved = runtimeResourcePool.enrichHeaders(data, headers,
                    resolvedRequest);
            return Optional.ofNullable(resolved)
                    .orElseGet(() -> new RuntimeResourcePool.ResolvedHeaders(List.copyOf(headers), List.of()));
        } catch (RuntimeException e) {
            logger.warn("Runtime response-header correlation failed; continuing without it: {}", e.getMessage());
            logger.debug("Runtime response-header correlation stacktrace", e);
            return new RuntimeResourcePool.ResolvedHeaders(List.copyOf(headers), List.of());
        }
    }

    private String applySuccessfulRequestBaseline(ServiceData data) {
        try {
            return Optional.ofNullable(runtimeResourcePool.applySuccessfulRequestBaseline(data)).orElse(data.getPayload());
        } catch (RuntimeException e) {
            logger.warn("Successful request baseline processing failed; continuing without it: {}", e.getMessage());
            logger.debug("Successful request baseline stacktrace", e);
            return data.getPayload();
        }
    }

    private RuntimeResourcePool.ResolvedRequest enrichWithRuntimeResources(ServiceData data, String processedPayload) {
        try {
            return runtimeResourcePool.enrich(data, processedPayload);
        } catch (RuntimeException e) {
            logger.warn("Runtime resource correlation failed while preparing the request; continuing without it: {}", e.getMessage());
            logger.debug("Runtime resource correlation stacktrace", e);
            return new RuntimeResourcePool.ResolvedRequest(processedPayload, data.getPathParamsPayload(), List.of());
        }
    }

    private void observeRuntimeResources(ServiceData data, CatsRequest request, CatsResponse response,
                                         RuntimeResourcePool.ResolvedRequest resolvedRequest) {
        try {
            runtimeResourcePool.observe(data, request, response, resolvedRequest);
        } catch (RuntimeException e) {
            logger.warn("Unable to store runtime resources from the response; keeping the service response unchanged: {}", e.getMessage());
            logger.debug("Runtime resource observation stacktrace", e);
        }
    }

    private void discardRuntimeCorrelations(RuntimeResourcePool.ResolvedRequest resolvedRequest) {
        try {
            runtimeResourcePool.discard(resolvedRequest);
        } catch (RuntimeException e) {
            logger.debug("Unable to discard unused runtime correlations", e);
        }
    }

    /**
     * Final url is being constructed by replacing path variables with the supplied urlParams or refData.
     * It also adds supplied query params if any.
     *
     * @param data             the service data context
     * @param processedPayload current payload
     * @return a url with path params replaced by urlParams or refData + additional query params
     */
    String constructUrl(ServiceData data, String processedPayload) {
        return constructUrl(data, processedPayload, data.getPathParamsPayload());
    }

    private String constructUrl(ServiceData data, String processedPayload, String pathParamsPayload) {
        String decodedUrl = CatsUtil.unescapeCurlyBrackets(apiArguments.getServer() + data.getRelativePath());
        logger.debug("Decoded URL: {}", SensitiveDataPolicy.maskUrl(decodedUrl, reportingArguments));
        if (!data.isReplaceUrlParams()) {
            String actualUrl = this.replacePathParams(decodedUrl, processedPayload, data);
            return this.addWfcAuthQueryParams(this.replaceRemovedParams(actualUrl));
        }

        String url = this.getPathWithRefDataReplacedForHttpEntityRequests(data, apiArguments.getServer() + data.getRelativePath());

        if (!HttpMethod.requiresBody(data.getHttpMethod())) {
            url = this.getPathWithRefDataReplacedForNonHttpEntityRequests(data, processedPayload, apiArguments.getServer() + data.getRelativePath());
            url = this.addUriParams(processedPayload, data, url);
        }
        url = this.addPathParamsIfNotReplaced(url, pathParamsPayload);
        if (HttpMethod.requiresBody(data.getHttpMethod())) {
            url = this.addQueryParamsFromPathParamsPayload(url, data, pathParamsPayload);
        }
        url = this.addAdditionalQueryParams(url, data);
        url = this.addWfcAuthQueryParams(url);
        logger.debug("Replaced URL: {}", SensitiveDataPolicy.maskUrl(url, reportingArguments));
        return url;
    }

    String addPathParamsIfNotReplaced(String url, String pathParamsPayload) {
        logger.debug("Using the following path params payload {} for path {}", pathParamsPayload, url);

        Set<String> pathVariables = OpenApiUtils.getPathVariables(url);
        logger.debug("Path variables found in the URL: {}", pathVariables);

        for (String pathVariable : pathVariables) {
            String pathValue = String.valueOf(JsonUtils.getVariableFromJson(pathParamsPayload, pathVariable));
            url = url.replace("{" + pathVariable + "}", CatsUtil.urlEncodePathSegment(pathValue));
        }
        return url;
    }

    String addQueryParamsFromPathParamsPayload(String url, ServiceData data) {
        return addQueryParamsFromPathParamsPayload(url, data, data.getPathParamsPayload());
    }

    private String addQueryParamsFromPathParamsPayload(String url, ServiceData data, String pathParamsPayload) {
        Set<String> queryParams = data.getQueryParams();

        if (StringUtils.isEmpty(pathParamsPayload) || queryParams.isEmpty()) {
            return url;
        }

        logger.debug("Adding query params {} from pathParamsPayload {} for body method", queryParams, pathParamsPayload);
        HttpUrl.Builder httpUrl = HttpUrl.get(url).newBuilder();

        for (String queryParam : queryParams) {
            Object paramValue = JsonUtils.getVariableFromJson(pathParamsPayload, queryParam);
            if (paramValue != null && !JsonUtils.NOT_SET.equals(String.valueOf(paramValue))) {
                String serializedValue = JsonUtils.serialize(paramValue);
                if (serializedValue != null) {
                    buildQueryParameter(queryParam, JsonParser.parseString(serializedValue), data)
                            .forEach(param -> httpUrl.addQueryParameter(param.getKey(), param.getValue()));
                }
            }
        }

        return httpUrl.build().toString();
    }

    String addAdditionalQueryParams(String startingUrl, String currentPath) {
        return addAdditionalQueryParams(startingUrl, currentPath, Map.of());
    }

    String addAdditionalQueryParams(String startingUrl, ServiceData data) {
        return addAdditionalQueryParams(startingUrl, data.getRelativePath(), getHeaderParserContext(data));
    }

    private String addAdditionalQueryParams(String startingUrl, String currentPath, Map<String, String> context) {
        HttpUrl.Builder httpUrl = HttpUrl.get(startingUrl).newBuilder();

        for (Map.Entry<String, Object> queryParamEntry : filesArguments.getAdditionalQueryParamsForPath(currentPath).entrySet()) {
            String value = DynamicValueResolver.resolve(String.valueOf(queryParamEntry.getValue()), context);
            httpUrl.addQueryParameter(queryParamEntry.getKey(), value);
        }

        return httpUrl.build().toString();
    }

    private EncodedRequestBody encodeRequestBody(String payload, ServiceData data) {
        if (!HttpMethod.requiresBody(data.getHttpMethod())) {
            return new EncodedRequestBody(payload, data.getContentType());
        }
        return switch (data.getPayloadFormat()) {
            case JSON -> new EncodedRequestBody(payload, data.getContentType());
            case FORM -> encodeFormPayload(payload, data);
            case TEXT -> new EncodedRequestBody(encodeTextPayload(payload, data.isValidJson()), data.getContentType());
            case NDJSON -> new EncodedRequestBody(encodeNdjsonPayload(payload, data.isValidJson()), data.getContentType());
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported request Content-Type '" + data.getContentType() + "'");
        };
    }

    private EncodedRequestBody encodeFormPayload(String payload, ServiceData data) {
        if (StringUtils.isBlank(payload) || !data.isValidJson()) {
            return new EncodedRequestBody(payload, data.getContentType());
        }
        try {
            HashMap<String, Object> payloadAsMap = new ObjectMapper().readValue(payload, new TypeReference<>() {
            });
            HttpContent content = FormEncoder.createHttpContent(payloadAsMap);
            if (!content.getContentType().startsWith("application/x-www-form-urlencoded")) {
                throw new IllegalArgumentException("Unable to encode request payload as application/x-www-form-urlencoded");
            }
            return new EncodedRequestBody(content.stringContent(), content.getContentType());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to encode request payload as application/x-www-form-urlencoded", e);
        }
    }

    private String encodeTextPayload(String payload, boolean validJson) {
        if (StringUtils.isBlank(payload) || !validJson) {
            return payload;
        }
        try {
            JsonElement element = JsonUtils.parseAsJsonElement(payload);
            return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                    ? element.getAsString() : payload;
        } catch (RuntimeException _) {
            return payload;
        }
    }

    private String encodeNdjsonPayload(String payload, boolean validJson) {
        if (StringUtils.isBlank(payload) || !validJson) {
            return payload;
        }
        try {
            JsonElement element = JsonUtils.parseAsJsonElement(payload);
            if (element.isJsonArray()) {
                List<String> records = new ArrayList<>();
                element.getAsJsonArray().forEach(record -> records.add(record.toString()));
                return records.isEmpty() ? "" : String.join("\n", records) + "\n";
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String raw = element.getAsString();
                boolean rawNdjson = !raw.isBlank() && raw.lines().filter(Predicate.not(String::isBlank)).allMatch(JsonUtils::isValidJson);
                if (rawNdjson) {
                    return raw.endsWith("\n") ? raw : raw + "\n";
                }
            }
            return element + "\n";
        } catch (RuntimeException _) {
            return payload;
        }
    }

    Map<String, String> getPathParamFromCorrespondingPostIfDelete(ServiceData data) {
        return runtimeResourcePool.resolvePathParameters(data);
    }


    List<KeyValuePair<String, Object>> buildHeaders(ServiceData data) {
        return buildHeaders(data, data.getContentType());
    }

    private List<KeyValuePair<String, Object>> buildHeaders(ServiceData data, String contentType) {
        List<KeyValuePair<String, Object>> headers = new ArrayList<>();
        String effectiveContentType = this.getContentType(data.getHttpMethod(), contentType);

        this.addMandatoryHeaders(data, effectiveContentType, headers);
        this.addSuppliedHeaders(data, headers);
        if (!data.isFuzzedHeader(HttpHeaders.CONTENT_TYPE)) {
            replaceHeaderWithUserSuppliedHeader(headers, HttpHeaders.CONTENT_TYPE, effectiveContentType);
        }
        this.addWfcAuthHeaders(headers);
        reportingArguments.registerSensitiveHeaders(wfcAuthProvider.getAuthenticationHeaderNames());
        reportingArguments.registerSensitiveQueryParams(wfcAuthProvider.getAuthenticationQueryParamNames());
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
    private String getPathWithRefDataReplacedForNonHttpEntityRequests(ServiceData data, String processedPayload, String startingUrl) {
        String actualUrl = this.filesArguments.replacePathWithUrlParams(startingUrl);

        if (StringUtils.isNotEmpty(processedPayload)) {
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
     * Calls the service with the provided request and response-validation fields.
     *
     * @param catsRequest  The CATS request to be sent to the service.
     * @param responseValidationFields fields whose presence may be validated in error responses
     * @return The CATS response received from the service.
     * @throws IOException If an I/O error occurs during the service call.
     */
    public CatsResponse callService(CatsRequest catsRequest, Set<String> responseValidationFields) throws IOException {
        acquireRateLimitPermit();
        long startTime = System.currentTimeMillis();
        RequestBody requestBody = null;
        Headers.Builder headers = new Headers.Builder();
        catsRequest.getHeaders().forEach(header -> headers.addUnsafeNonAscii(header.getKey(), String.valueOf(header.getValue())));

        if (HttpMethod.requiresBody(catsRequest.getHttpMethod())) {
            String contentType = headers.get(HttpHeaders.CONTENT_TYPE);
            requestBody = RequestBody.create(catsRequest.getPayload().getBytes(StandardCharsets.UTF_8),
                    contentType == null ? null : MediaType.parse(contentType));
        } else {
            //for GET and HEAD, we remove Content-Type as some servers don't like it
            headers.removeAll("Content-Type");
        }
        Request request = new Request.Builder()
                .url(catsRequest.getUrl())
                .headers(headers.build())
                .method(catsRequest.getHttpMethod(), requestBody)
                .build();
        testCaseListener.recordRequestAttempt();
        try (Response response = okHttpClient.newCall(request).execute()) {
            long endTime = System.currentTimeMillis();

            CatsResponse.CatsResponseBuilder catsResponseBuilder = this.populateCatsResponseFromHttpResponse(response);
            CatsResponse catsResponse = catsResponseBuilder.httpMethod(catsRequest.getHttpMethod())
                    .responseTimeInMs(endTime - startTime)
                    .path(catsRequest.getUrl())
                    .responseValidationField(responseValidationFields.stream().findAny()
                            .map(el -> el.substring(el.lastIndexOf("#") + 1)).orElse(null))
                    .build();

            logger.complete("Protocol: {}, Method: {}, ResponseCode: {}, ResponseTimeInMs: {}, ResponseLength: {}, ResponseWords: {}, ResponseLines: {}",
                    response.protocol(), catsResponse.getHttpMethod(), catsResponse.responseCodeAsString(), endTime - startTime,
                    catsResponse.getContentLengthInBytes(), catsResponse.getNumberOfWordsInResponse(), catsResponse.getNumberOfLinesInResponse());

            return catsResponse;
        }
    }

    private void acquireRateLimitPermit() {
        CatsExecutionCancelledException.check();
        rateLimiter.acquire();
        CatsExecutionCancelledException.check();
    }

    private CatsResponse.CatsResponseBuilder populateCatsResponseFromHttpResponse(Response response) throws IOException {
        List<KeyValuePair<String, String>> responseHeaders = response.headers()
                .toMultimap()
                .entrySet().stream()
                .map(header -> new KeyValuePair<>(header.getKey(), header.getValue().getFirst())).toList();

        BoundedResponseBodyReader.CapturedBody capturedResponse = BoundedResponseBodyReader.read(response, apiArguments.getMaxResponseBytes());
        String rawResponse = capturedResponse.body();
        String jsonResponse = JsonUtils.getAsJsonString(rawResponse);
        String responseContentType = this.getResponseContentType(response);

        int numberOfWords = new StringTokenizer(rawResponse).countTokens();
        int numberOfLines = rawResponse.split("[\r|\n]").length;

        logger.debug("Raw response body: {}", rawResponse);
        logger.debug("Raw response headers: {}", SensitiveDataPolicy.maskHeadersForDisplay(responseHeaders, reportingArguments));

        return CatsResponse.builder()
                .responseCode(response.code())
                .headers(responseHeaders)
                .body(rawResponse)
                .bodyTruncated(capturedResponse.truncated())
                .capturedBodyBytes(capturedResponse.capturedBytes())
                .declaredContentLength(capturedResponse.declaredContentLength())
                .responseBodyLimit(apiArguments.getMaxResponseBytes())
                .jsonBody(JsonParser.parseString(jsonResponse))
                .numberOfLinesInResponse(numberOfLines)
                .contentLengthInBytes(capturedResponse.capturedBytes())
                .responseContentType(responseContentType)
                .numberOfWordsInResponse(numberOfWords);
    }

    private String getResponseContentType(Response response) {
        MediaType defaultResponseMediaType = MediaType.parse(CatsResponse.unknownContentType());
        return String.valueOf(Optional.ofNullable(response.body().contentType()).orElse(defaultResponseMediaType));
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
                            return currentPath.replace(pathElement, CatsUtil.urlEncodePathSegment(String.valueOf(pathElementValue)));
                        });
    }

    private void addMandatoryHeaders(ServiceData data, String contentType, List<KeyValuePair<String, Object>> headers) {
        data.getHeaders().forEach(header -> headers.add(new KeyValuePair<>(header.getName(), header.getValue())));
        addIfNotPresent(HttpHeaders.ACCEPT, processingArguments.getDefaultContentType(), data, headers);
        addIfNotPresent(HttpHeaders.CONTENT_TYPE, contentType, data, headers);
        addIfNotPresent(HttpHeaders.USER_AGENT, apiArguments.getUserAgent(testCaseListener.getCurrentTestCaseNumber(), testCaseListener.getCurrentFuzzer()), data, headers);
        addIfNotPresent(CATS_HEADER_UUID, testCaseListener.getTestIdentifier(), data, headers);
    }

    private String getContentType(HttpMethod method, String defaultContentType) {
        return method == HttpMethod.PATCH && processingArguments.isRfc7396() ? JsonUtils.JSON_PATCH : defaultContentType;
    }

    private void addIfNotPresent(String headerName, String headerValue, ServiceData data, List<KeyValuePair<String, Object>> headers) {
        boolean notExists = data.getHeaders().stream().noneMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(headerName));
        if (notExists) {
            headers.add(new KeyValuePair<>(headerName, headerValue));
        }
    }

    private List<KeyValuePair<String, String>> buildQueryParameters(String payload, ServiceData data) {
        List<KeyValuePair<String, String>> queryParams = new ArrayList<>();
        JsonElement jsonElement = JsonUtils.parseOrConvertToJsonElement(payload);
        Map<String, QueryParameterSerialization> serializations = Optional.ofNullable(data.getQueryParameterSerializations())
                .orElseGet(Collections::emptyMap);

        for (Map.Entry<String, JsonElement> child : ((JsonObject) jsonElement).entrySet()) {
            boolean namedQueryParameter = data.getQueryParams().contains(child.getKey())
                    || serializations.containsKey(child.getKey())
                    || CatsDSLWords.isExtraField(child.getKey());
            if (child.getValue().isJsonObject() && !namedQueryParameter) {
                queryParams.addAll(this.buildQueryParameters(child.getValue().toString(), data));
            } else if (!data.getPathParams().contains(child.getKey()) || data.getQueryParams().contains(child.getKey()) || CatsDSLWords.isExtraField(child.getKey())) {
                if (child.getValue().isJsonNull()) {
                    logger.debug("Not adding null query parameter {}", child.getKey());
                } else {
                    queryParams.addAll(buildQueryParameter(child.getKey(), child.getValue(), data));
                }
            }
        }
        return queryParams;
    }

    private List<KeyValuePair<String, String>> buildQueryParameter(String name, JsonElement value, ServiceData data) {
        QueryParameterSerialization serialization = Optional.ofNullable(data.getQueryParameterSerializations())
                .orElseGet(Collections::emptyMap)
                .getOrDefault(name, QueryParameterSerialization.defaults());

        if (value.isJsonObject()) {
            return buildObjectQueryParameter(name, value.getAsJsonObject(), serialization);
        }
        if (!value.isJsonArray()) {
            return List.of(new KeyValuePair<>(name, value.getAsString()));
        }

        List<String> values = queryParameterValues(value.getAsJsonArray());
        if (values.isEmpty()) {
            return Collections.emptyList();
        }

        if (QueryParameterSerialization.FORM.equals(serialization.style()) && serialization.explode()) {
            return values.stream().map(item -> new KeyValuePair<>(name, item)).toList();
        }

        return List.of(new KeyValuePair<>(name, String.join(queryParameterDelimiter(serialization), values)));
    }

    private List<KeyValuePair<String, String>> buildObjectQueryParameter(String name, JsonObject value,
                                                                          QueryParameterSerialization serialization) {
        if (QueryParameterSerialization.DEEP_OBJECT.equals(serialization.style())) {
            List<KeyValuePair<String, String>> parameters = new ArrayList<>();
            value.entrySet().forEach(entry -> addDeepObjectQueryParameters(
                    name + "[" + entry.getKey() + "]", entry.getValue(), parameters));
            return List.copyOf(parameters);
        }

        if (QueryParameterSerialization.FORM.equals(serialization.style()) && serialization.explode()) {
            return value.entrySet().stream()
                    .filter(entry -> !entry.getValue().isJsonNull())
                    .map(entry -> new KeyValuePair<>(entry.getKey(), queryParameterValue(entry.getValue())))
                    .toList();
        }

        List<String> values = new ArrayList<>();
        value.entrySet().stream()
                .filter(entry -> !entry.getValue().isJsonNull())
                .forEach(entry -> {
                    values.add(entry.getKey());
                    values.add(queryParameterValue(entry.getValue()));
                });
        if (values.isEmpty()) {
            return Collections.emptyList();
        }

        return List.of(new KeyValuePair<>(name, String.join(queryParameterDelimiter(serialization), values)));
    }

    private String queryParameterDelimiter(QueryParameterSerialization serialization) {
        return switch (serialization.style()) {
            case "spaceDelimited" -> " ";
            case "pipeDelimited" -> "|";
            default -> ",";
        };
    }

    private void addDeepObjectQueryParameters(String name, JsonElement value,
                                               List<KeyValuePair<String, String>> parameters) {
        if (value.isJsonNull()) {
            return;
        }
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> addDeepObjectQueryParameters(
                    name + "[" + entry.getKey() + "]", entry.getValue(), parameters));
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(item -> addDeepObjectQueryParameters(name, item, parameters));
        } else {
            parameters.add(new KeyValuePair<>(name, value.getAsString()));
        }
    }

    private List<String> queryParameterValues(Iterable<JsonElement> values) {
        List<String> result = new ArrayList<>();
        values.forEach(item -> {
            if (!item.isJsonNull()) {
                result.add(queryParameterValue(item));
            }
        });
        return result;
    }

    private String queryParameterValue(JsonElement value) {
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
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
        return BoundedResponseBodyReader.read(response, apiArguments.getMaxResponseBytes()).body();
    }

    private void recordServiceData(ServiceData serviceData) {
        testCaseListener.addPath(serviceData.getContractPath());
        testCaseListener.addContractPath(serviceData.getContractPath());
        testCaseListener.addServer(apiArguments.getServer());
        testCaseListener.addValidJson(serviceData.isValidJson());
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
        logger.debug("Path {} (including ALL headers) has the following headers: {}", data.getContractPath(),
                SensitiveDataPolicy.maskHeadersForDisplay(userSuppliedHeaders, reportingArguments));

        Map<String, String> headerParserContext = getHeaderParserContext(data);
        Map<String, String> suppliedHeaders = userSuppliedHeaders.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> DynamicValueResolver.resolve(String.valueOf(entry.getValue()), headerParserContext)));

        for (Map.Entry<String, String> suppliedHeader : suppliedHeaders.entrySet()) {
            if (data.isAddUserHeaders()) {
                this.replaceHeaderIfNotFuzzed(headers, data, suppliedHeader);
            } else if (this.isSuppliedHeaderInFuzzData(data, suppliedHeader) || this.isAuthenticationHeader(suppliedHeader.getKey())) {
                replaceHeaderWithUserSuppliedHeader(headers, suppliedHeader.getKey(), suppliedHeader.getValue());
            }
        }
    }

    private Map<String, String> getHeaderParserContext(ServiceData data) {
        Map<String, String> context = new HashMap<>(authArguments.getDynamicVariablesContext());
        context.putAll(data.getDynamicVariables());
        return context;
    }

    private static void replaceHeaderWithUserSuppliedHeader(List<KeyValuePair<String, Object>> headers, String headerName, Object headerValue) {
        /* We need to make sure we add the same number of headers back as this is important for some Fuzzers*/
        Predicate<KeyValuePair<String, Object>> headersToFilter = header -> header.getKey().equalsIgnoreCase(headerName);
        long howManyHeadersToRemove = Math.max(headers.stream().filter(headersToFilter).count(), 1);
        headers.removeIf(headersToFilter);

        LongStream.range(0, howManyHeadersToRemove)
                .forEach(_ -> headers.add(new KeyValuePair<>(headerName, headerValue)));
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
        Set<String> wfcHeaders = wfcAuthProvider == null ? Set.of() : wfcAuthProvider.getAuthenticationHeaderNames();
        return SensitiveDataPolicy.isSensitiveHeader(header, wfcHeaders, false);
    }

    public Map<String, String> getEnvironmentVariables() {
        return authArguments.getEnvironmentVariables();
    }

    /**
     * Gets authentication headers added by runtime auth providers but not necessarily present in the OpenAPI contract or headers file.
     *
     * @return runtime authentication header names
     */
    public Set<String> getAuthenticationHeaderNames() {
        return wfcAuthProvider.getAuthenticationHeaderNames();
    }

    private void addWfcAuthHeaders(List<KeyValuePair<String, Object>> headers) {
        wfcAuthProvider.getHeaders(okHttpClient)
                .forEach((name, value) -> replaceHeaderWithUserSuppliedHeader(headers, name, value));
    }

    private String addWfcAuthQueryParams(String url) {
        return wfcAuthProvider.applyQueryParams(url, okHttpClient);
    }

    private void replaceHeaderIfNotFuzzed(List<KeyValuePair<String, Object>> headers, ServiceData data, Map.Entry<String, String> suppliedHeader) {
        if (!data.isFuzzedHeader(suppliedHeader.getKey())) {
            replaceHeaderWithUserSuppliedHeader(headers, suppliedHeader.getKey(), suppliedHeader.getValue());
        } else {
            /* There are 2 cases when we want to mix the supplied header with the fuzzed one: if the fuzzing is TRAIL or PREFIX we want to try this behavior on a valid header value */
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
            String valueToReplace = CatsDSLParser.parseAndGetResult(String.valueOf(entry.getValue()), authArguments.getDynamicVariablesContext());
            currentUrl = currentUrl.replace("{" + entry.getKey() + "}", CatsUtil.urlEncodePathSegment(valueToReplace));
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
        return replacePayloadWithRefData(data, data.getPayload());
    }

    private String replacePayloadWithRefData(ServiceData data, String initialPayload) {
        if (!data.getPayloadFormat().supportsNamedFields() || !data.isReplaceRefData() || "null".equals(initialPayload)) {
            logger.note("Bypassing reference data replacement for path {}!", data.getRelativePath());
            return initialPayload;
        } else {
            Map<String, Object> refDataForCurrentPath = filesArguments.getRefData(data.getRelativePath());
            logger.debug("Payload reference data replacement: path {} has the following reference data: {}", data.getRelativePath(), refDataForCurrentPath);

            Map<String, Object> refDataWithoutAdditionalProperties =
                    refDataForCurrentPath.entrySet().stream()
                            .filter(e -> !e.getKey().matches(ADDITIONAL_PROPERTIES))
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> Objects.requireNonNullElse(e.getValue(), SUBSTITUTE_FOR_NULL)));
            String payload = initialPayload;

            /*this will override refData for DELETE requests in order to provide valid entities that will get deleted*/
            refDataWithoutAdditionalProperties.putAll(this.getPathParamFromCorrespondingPostIfDelete(data));

            for (Map.Entry<String, Object> entry : refDataWithoutAdditionalProperties.entrySet()) {
                payload = replaceRefDataEntry(data, entry, payload);
            }

            payload = CatsUtil.setAdditionalPropertiesToPayload(refDataForCurrentPath, payload);

            logger.debug("Final payload after reference data replacement: {}", payload);

            return payload;
        }
    }

    private String replaceRefDataEntry(ServiceData data, Map.Entry<String, Object> entry, String payload) {
        Object refDataValue = entry.getValue();
        if (refDataValue instanceof String str) {
            Map<String, String> context = new HashMap<>(authArguments.getDynamicVariablesContext());
            context.put(Parser.REQUEST, payload);
            refDataValue = CatsDSLParser.parseAndGetResult(str, context);
        }
        if (SUBSTITUTE_FOR_NULL.equals(String.valueOf(refDataValue))) {
            refDataValue = null;
        }
        try {
            if (CATS_REMOVE_FIELD.equalsIgnoreCase(String.valueOf(refDataValue))) {
                payload = JsonUtils.deleteNode(payload, entry.getKey());
            } else {
                logger.debug("Replacing field {} with value {}", entry.getKey(), refDataValue);
                FuzzingStrategy fuzzingStrategy = FuzzingStrategy.replace().withData(refDataValue);
                boolean mergeFuzzing = data.isFuzzedField(entry.getKey(), RequestTarget.Location.BODY) ||
                        data.isFuzzedField(entry.getKey(), RequestTarget.Location.PATH) ||
                        data.isFuzzedField(entry.getKey(), RequestTarget.Location.QUERY);
                payload = FuzzingStrategy.replaceField(payload, entry.getKey(), fuzzingStrategy, mergeFuzzing).json();
            }
        } catch (PathNotFoundException _) {
            logger.debug("Ref data key {} was not found within the payload!", entry.getKey());
        }
        return payload;
    }

    private record EncodedRequestBody(String payload, String contentType) {
    }
}
