package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies sibling numeric triples representing financial or quantity calculations
 * (such as {@code unitPrice * quantity = totalAmount} or {@code subtotal + taxAmount = grandTotal})
 * and sends a contradicting total to detect backends that persist unvalidated client-calculated totals.
 */
@Singleton
@FieldFuzzer
public class ArithmeticTotalMismatchFieldsFuzzer implements Fuzzer {
    static final int FIRST_COMPONENT_VALUE = 10;
    static final int SECOND_COMPONENT_VALUE = 2;
    static final int TAMPERED_TOTAL_CANARY = 98765;

    record ArithmeticPattern(Set<String> firstCandidates, Set<String> secondCandidates, Set<String> totalCandidates) {
    }

    record ArithmeticTriple(String firstField, String secondField, String totalField) {
    }

    private static final List<ArithmeticPattern> ARITHMETIC_PATTERNS = List.of(
            new ArithmeticPattern(
                    Set.of("unitprice", "price", "itemprice", "rate"),
                    Set.of("quantity", "qty", "itemcount", "units"),
                    Set.of("total", "linetotal", "totalprice", "totalamount", "amount")),
            new ArithmeticPattern(
                    Set.of("subtotal", "netamount", "baseamount"),
                    Set.of("tax", "taxamount", "vat", "vatamount", "fee", "shippingamount"),
                    Set.of("total", "totalamount", "grossamount", "grandtotal"))
    );

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ArithmeticTotalMismatchFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new ArithmeticTotalMismatchFieldsFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run the fuzz logic
     * @param testCaseListener the test case listener for reporting results
     */
    public ArithmeticTotalMismatchFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        List<ArithmeticTriple> triples = findArithmeticTriples(data);
        if (triples.isEmpty()) {
            logger.skip("No arithmetic component/total field triples found in the request");
            return;
        }
        triples.forEach(triple -> fuzzTriple(data, triple));
    }

    private void fuzzTriple(FuzzingData data, ArithmeticTriple triple) {
        String step1 = CatsUtil.justReplaceField(data.getPayload(), triple.firstField(), FIRST_COMPONENT_VALUE).json();
        String step2 = CatsUtil.justReplaceField(step1, triple.secondField(), SECOND_COMPONENT_VALUE).json();
        String mutatedPayload = CatsUtil.justReplaceField(step2, triple.totalField(), TAMPERED_TOTAL_CANARY).json();

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mutatedPayload)
                        .mutationTargets(RequestTargetResolver.resolvePayloadFields(
                                data, List.of(triple.firstField(), triple.secondField(), triple.totalField())))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .scenario("Send inconsistent total [%s=%d] alongside [%s=%d] and [%s=%d] to verify server-side total calculation"
                                .formatted(triple.totalField(), TAMPERED_TOTAL_CANARY,
                                        triple.firstField(), FIRST_COMPONENT_VALUE,
                                        triple.secondField(), SECOND_COMPONENT_VALUE))
                        .responseProcessor((response, fuzzData) -> processResponse(response, fuzzData, triple))
                        .build()
        );
    }

    private void processResponse(CatsResponse response, FuzzingData data, ArithmeticTriple triple) {
        int code = response.getResponseCode();
        if (ResponseCodeFamily.is4xxCode(code)) {
            testCaseListener.reportResultInfo(logger, data, "Inconsistent arithmetic total rejected",
                    "The API rejected the inconsistent total for [%s] (status %d)".formatted(triple.totalField(), code));
            return;
        }
        if (ResponseCodeFamily.is2xxCode(code) && isTotalCanaryReflected(response.getBody(), triple.totalField())) {
            testCaseListener.reportResultError(logger, data,
                    CatsResultFactory.Reason.ARITHMETIC_TOTAL_MISMATCH_ACCEPTED.value(),
                    "Inconsistent client-supplied total [%s=%d] was accepted and reflected in the response (status %d)"
                            .formatted(triple.totalField(), TAMPERED_TOTAL_CANARY, code));
            return;
        }
        if (ResponseCodeFamily.is5xxCode(code)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when processing inconsistent total [%s]".formatted(code, triple.totalField()));
            return;
        }
        testCaseListener.reportResultInfo(logger, data, "Client-supplied total ignored or recomputed by server",
                "Write request succeeded (status %d) without reflecting the tampered total for [%s]"
                        .formatted(code, triple.totalField()));
    }

    static boolean isTotalCanaryReflected(String responseBody, String totalField) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String leaf = totalField.substring(totalField.lastIndexOf('#') + 1).toLowerCase(Locale.ROOT);
        String bodyLower = responseBody.toLowerCase(Locale.ROOT);
        return bodyLower.contains("\"" + leaf + "\"") && bodyLower.contains(String.valueOf(TAMPERED_TOTAL_CANARY));
    }

    static List<ArithmeticTriple> findArithmeticTriples(FuzzingData data) {
        Set<String> numericFields = Optional.ofNullable(data.getAllFieldsByHttpMethod()).orElse(Set.of()).stream()
                .filter(field -> isNumericFieldInPayload(data, field))
                .collect(java.util.stream.Collectors.toSet());
        return ARITHMETIC_PATTERNS.stream()
                .flatMap(pattern -> matchPatternTriples(numericFields, pattern).stream())
                .sorted(Comparator.comparing(ArithmeticTriple::totalField).thenComparing(ArithmeticTriple::firstField))
                .toList();
    }

    private static List<ArithmeticTriple> matchPatternTriples(Set<String> numericFields, ArithmeticPattern pattern) {
        return numericFields.stream()
                .filter(f -> pattern.firstCandidates().contains(WordUtils.normalizeLeaf(f)))
                .flatMap(first -> numericFields.stream()
                        .filter(second -> sameParentPrefix(first, second)
                                && pattern.secondCandidates().contains(WordUtils.normalizeLeaf(second)))
                        .flatMap(second -> numericFields.stream()
                                .filter(total -> sameParentPrefix(first, total)
                                        && !total.equals(first) && !total.equals(second)
                                        && pattern.totalCandidates().contains(WordUtils.normalizeLeaf(total)))
                                .map(total -> new ArithmeticTriple(first, second, total))))
                .toList();
    }

    private static boolean isNumericFieldInPayload(FuzzingData data, String field) {
        Object val = JsonUtils.getVariableFromJson(data.getPayload(), field);
        if (val instanceof Number) {
            return true;
        }
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(field);
        return (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema))
                && JsonUtils.isFieldInJson(data.getPayload(), field);
    }

    private static boolean sameParentPrefix(String firstField, String secondField) {
        int idx1 = firstField.lastIndexOf('#');
        int idx2 = secondField.lastIndexOf('#');
        String p1 = idx1 >= 0 ? firstField.substring(0, idx1) : "";
        String p2 = idx2 >= 0 ? secondField.substring(0, idx2) : "";
        return p1.equals(p2);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sends a total field that contradicts its sibling component fields (e.g., unitPrice * quantity != totalAmount) to verify server-side total calculation";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
