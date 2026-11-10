package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies that {@code GET} collection endpoints declaring a numeric pagination limit query parameter
 * ({@code limit}, {@code size}, {@code pageSize}, {@code per_page}, {@code maxResults}, {@code top})
 * actually honor {@code limit=1} in the returned JSON collection.
 */
@Singleton
@HttpFuzzer
public class PaginationLimitHonoredFuzzer implements Fuzzer {
    private static final Set<String> LIMIT_PARAM_NAMES = Set.of(
            "limit", "size", "pagesize", "perpage", "maxresults", "top"
    );

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PaginationLimitHonoredFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new PaginationLimitHonoredFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run HTTP calls
     * @param testCaseListener the test case listener for reporting results
     */
    public PaginationLimitHonoredFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() != HttpMethod.GET) {
            return;
        }
        Optional<String> limitParamOpt = findEligibleLimitParam(data);
        if (limitParamOpt.isEmpty()) {
            logger.skip("No numeric pagination limit query parameter allowing 1 found on GET {}", data.getPath());
            return;
        }
        String limitParam = limitParamOpt.get();
        String mutatedPayload = CatsUtil.justReplaceField(data.getPayload(), limitParam, 1).json();

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .mutationTarget(RequestTarget.query(limitParam))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .scenario("Send GET request with pagination parameter [%s=1] and verify at most 1 item is returned"
                                .formatted(limitParam))
                        .responseProcessor((response, context) -> checkResponse(response, context, limitParam))
                        .build()
        );
    }

    private void checkResponse(CatsResponse response, FuzzingData data, String limitParam) {
        int code = response.getResponseCode();
        if (ResponseCodeFamily.is5xxCode(code)) {
            testCaseListener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "GET with pagination parameter [ {}=1 ] returned {}", limitParam, response.responseCodeAsString());
            return;
        }
        if (!ResponseCodeFamily.is2xxCode(code) || response.isBodyTruncated() || !JsonUtils.isValidJson(response.getBody())) {
            testCaseListener.reportResultInfo(logger, data, "Pagination limit check inconclusive (status {})",
                    response.responseCodeAsString());
            return;
        }
        Optional<Integer> collectionSize = extractCollectionSize(JsonUtils.parseAsJsonElement(response.getBody()));
        if (collectionSize.isEmpty()) {
            testCaseListener.reportResultInfo(logger, data,
                    "Response for GET {} did not contain an inspectable JSON collection array", data.getPath());
            return;
        }
        int size = collectionSize.get();
        if (size > 1) {
            testCaseListener.reportResultWarn(logger, data, Reason.PAGINATION_LIMIT_IGNORED.value(),
                    "Requested pagination limit [{}=1] on GET {}, but response collection returned {} items",
                    limitParam, data.getPath(), size);
        } else {
            testCaseListener.reportResultInfo(logger, data,
                    "Pagination limit [{}=1] honored ({} item(s) returned)", limitParam, size);
        }
    }

    static Optional<Integer> extractCollectionSize(JsonElement root) {
        return ResponseCollections.items(root).map(JsonArray::size);
    }

    static Optional<String> findEligibleLimitParam(FuzzingData data) {
        Set<String> queryParams = Optional.ofNullable(data.getQueryParams()).orElse(Set.of());
        return queryParams.stream()
                .sorted()
                .filter(param -> LIMIT_PARAM_NAMES.contains(WordUtils.normalizeLeaf(param)))
                .filter(param -> JsonUtils.isFieldInJson(data.getPayload(), param))
                .filter(param -> allowsLimitOne(data, param))
                .findFirst();
    }

    private static boolean allowsLimitOne(FuzzingData data, String param) {
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(param);
        if (schema == null) {
            return true;
        }
        if (!CatsModelUtils.isIntegerSchema(schema) && !CatsModelUtils.isNumberSchema(schema)) {
            return false;
        }
        BigDecimal min = schema.getMinimum();
        return min == null || min.compareTo(BigDecimal.ONE) <= 0;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sends limit=1 (or size=1 / pageSize=1 / per_page=1) on GET collection endpoints and warns if the returned array contains more than 1 item";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
