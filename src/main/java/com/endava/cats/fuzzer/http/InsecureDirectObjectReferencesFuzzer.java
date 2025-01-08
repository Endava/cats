package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fuzzer that detects Insecure Direct Object Reference (IDOR) vulnerabilities.
 * <p>
 * IDOR occurs when an application exposes internal object references (like database IDs)
 * and fails to verify that the user has authorization to access the referenced object.
 * </p>
 * <p>
 * This fuzzer identifies ID-like fields in the request and replaces them with different values
 * to check if the API returns data belonging to other users/resources.
 * </p>
 */
@HttpFuzzer
@Singleton
public class InsecureDirectObjectReferencesFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(InsecureDirectObjectReferencesFuzzer.class);

    private static final List<String> ID_FIELD_PATTERNS = Arrays.asList(
            "id", "uuid", "guid", "userid", "user_id", "accountid", "account_id",
            "customerid", "customer_id", "orderid", "order_id", "resourceid", "resource_id",
            "objectid", "object_id", "entityid", "entity_id", "recordid", "record_id",
            "documentid", "document_id", "fileid", "file_id", "itemid", "item_id"
    );

    private static final Pattern ID_SUFFIX_PATTERN = Pattern.compile(".*[_-]?(?:id|uuid|guid)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final FilesArguments filesArguments;

    /**
     * Creates a new InsecureDirectObjectReferencesFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run the fuzz logic
     * @param testCaseListener the test case listener for reporting results
     */
    public InsecureDirectObjectReferencesFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, FilesArguments filesArguments) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.filesArguments = filesArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> allFields = data.getRequestPropertyTypes().keySet();
        List<String> idFields = findIdFields(allFields);

        if (idFields.isEmpty()) {
            LOGGER.skip("No ID-like fields found in the request");
            return;
        }

        LOGGER.info("Found {} ID-like field(s) to test for IDOR: {}", idFields.size(), idFields);

        for (String idField : idFields) {
            boolean isRefDataField = filesArguments != null && filesArguments.getRefData(data.getPath()).get(idField) != null;
            if (!isRefDataField) {
                fuzzIdField(data, idField);
            } else {
                LOGGER.skip("Field {} is reference data, skipping", idField);
            }
        }
    }

    private List<String> findIdFields(Set<String> allFields) {
        List<String> idFields = new ArrayList<>();

        for (String field : allFields) {
            String fieldName = getSimpleFieldName(field);
            if (isIdField(fieldName)) {
                idFields.add(field);
            }
        }

        return idFields;
    }

    private String getSimpleFieldName(String field) {
        if (field.contains("#")) {
            return field.substring(field.lastIndexOf('#') + 1);
        }
        return field;
    }

    private boolean isIdField(String fieldName) {
        String lowerFieldName = fieldName.toLowerCase(Locale.ROOT);

        if (ID_FIELD_PATTERNS.contains(lowerFieldName)) {
            return true;
        }

        return ID_SUFFIX_PATTERN.matcher(fieldName).matches();
    }

    private void fuzzIdField(FuzzingData data, String idField) {
        Object currentValue = JsonUtils.getVariableFromJson(data.getPayload(), idField);

        if (JsonUtils.isNotSet(String.valueOf(currentValue))) {
            LOGGER.debug("Field {} has no value set, skipping", idField);
            return;
        }

        List<Object> alternativeIds = generateAlternativeIds(currentValue);

        for (Object alternativeId : alternativeIds) {
            String fuzzedPayload = CatsUtil.justReplaceField(data.getPayload(), idField, alternativeId).json();

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_AA)
                            .fuzzingData(data)
                            .logger(LOGGER)
                            .scenario("Replace ID field [%s] with alternative value [%s] to test for IDOR vulnerability. Original value: [%s]"
                                    .formatted(idField, alternativeId, currentValue))
                            .fuzzer(this)
                            .payload(fuzzedPayload)
                            .responseProcessor(this::checkForIdorVulnerability)
                            .build()
            );
        }
    }

    private List<Object> generateAlternativeIds(Object currentValue) {
        List<Object> alternatives = new ArrayList<>();
        String valueStr = String.valueOf(currentValue);

        if (UUID_PATTERN.matcher(valueStr).matches()) {
            alternatives.add(UUID.randomUUID().toString());
            alternatives.add("00000000-0000-0000-0000-000000000000");
            alternatives.add("11111111-1111-1111-1111-111111111111");
        } else if (NUMERIC_ID_PATTERN.matcher(valueStr).matches()) {
            long numericValue = Long.parseLong(valueStr);
            alternatives.add(numericValue + 1);
            alternatives.add(numericValue - 1);
            alternatives.add(1L);
            alternatives.add(0L);
            alternatives.add(NumberGenerator.generateRandomLong(1, 999999));
        } else {
            alternatives.add(valueStr + "_other");
            alternatives.add("other_" + valueStr);
            alternatives.add("test_user_id");
            alternatives.add(UUID.randomUUID().toString());
        }

        return alternatives;
    }

    private void checkForIdorVulnerability(CatsResponse response, FuzzingData data) {
        int responseCode = response.getResponseCode();

        if (ResponseCodeFamily.is2xxCode(responseCode)) {
            testCaseListener.reportResultError(LOGGER, data,
                    "Potential IDOR vulnerability detected",
                    ("Request with modified ID returned success [%s]. The API may be exposing data belonging to other users/resources. " +
                            "Expected [4XX] response indicating unauthorized access.").formatted(response.responseCodeAsString()));
            return;
        }

        if (ResponseCodeFamily.is4xxCode(responseCode)) {
            testCaseListener.reportResultInfo(LOGGER, data,
                    "IDOR payload rejected",
                    "Request with modified ID correctly returned %s - access properly denied or resource not found"
                            .formatted(response.responseCodeAsString()));
            return;
        }

        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(LOGGER, data,
                    "Server error with IDOR payload",
                    "Server returned %d error when processing modified ID. This may indicate improper error handling."
                            .formatted(responseCode));
            return;
        }

        testCaseListener.reportResultError(LOGGER, data,
                "Unexpected response code",
                "Received unexpected response code %d for IDOR payload. Expected [4XX] for proper access control."
                        .formatted(responseCode));
    }

    @Override
    public String description() {
        return "detects Insecure Direct Object Reference (IDOR) vulnerabilities by replacing ID fields with alternative values";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
