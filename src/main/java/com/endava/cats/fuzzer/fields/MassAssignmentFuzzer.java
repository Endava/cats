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
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fuzzer that tests for Mass Assignment vulnerabilities by adding extra fields
 * to the request payload that are not defined in the OpenAPI schema.
 * <p>
 * Mass Assignment (also known as Auto-binding or Object Injection) occurs when
 * an API blindly binds client-provided data to internal objects without proper
 * filtering. This can allow attackers to modify fields they shouldn't have access to.
 * </p>
 * <p>
 * This fuzzer leverages CATS's schema knowledge to add fields that are NOT in the
 * schema and checks if the API accepts or reflects them.
 * </p>
 */
@Singleton
@FieldFuzzer
public class MassAssignmentFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Sensitive fields that should not be user-controllable.
     * These are common fields that could lead to privilege escalation or data manipulation.
     */
    private static final Map<String, Object> SENSITIVE_FIELDS = Map.ofEntries(
            // Privilege escalation fields
            Map.entry("role", "admin"),
            Map.entry("roles", List.of("admin", "superuser")),
            Map.entry("isAdmin", true),
            Map.entry("admin", true),
            Map.entry("is_admin", true),
            Map.entry("permissions", List.of("*", "admin:*")),
            Map.entry("privilege", "admin"),
            Map.entry("privileges", List.of("admin")),
            Map.entry("accessLevel", "admin"),
            Map.entry("access_level", 999),
            Map.entry("userType", "admin"),
            Map.entry("user_type", "administrator"),
            Map.entry("accountType", "premium"),

            // Status manipulation fields
            Map.entry("verified", true),
            Map.entry("emailVerified", true),
            Map.entry("email_verified", true),
            Map.entry("approved", true),
            Map.entry("active", true),
            Map.entry("enabled", true),
            Map.entry("status", "active"),
            Map.entry("accountStatus", "verified"),
            Map.entry("banned", false),
            Map.entry("suspended", false),

            // Financial fields
            Map.entry("balance", 999999),
            Map.entry("credits", 999999),
            Map.entry("points", 999999),
            Map.entry("price", 0),
            Map.entry("discount", 100),
            Map.entry("discountPercent", 100),
            Map.entry("amount", 0),
            Map.entry("fee", 0),

            // Ownership fields
            Map.entry("userId", "attacker-id"),
            Map.entry("user_id", "attacker-id"),
            Map.entry("ownerId", "attacker-id"),
            Map.entry("owner_id", "attacker-id"),
            Map.entry("createdBy", "attacker"),
            Map.entry("created_by", "attacker"),
            Map.entry("organizationId", "attacker-org"),
            Map.entry("tenantId", "attacker-tenant"),

            // Internal fields
            Map.entry("internal", true),
            Map.entry("isInternal", true),
            Map.entry("debug", true),
            Map.entry("test", true)
    );

    /**
     * Creates a new MassAssignmentFuzzer instance.
     *
     * @param simpleExecutor   the executor used to run the fuzz logic
     * @param testCaseListener the test case listener for reporting results
     */
    public MassAssignmentFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }

        Set<String> existingFields = data.getAllFieldsByHttpMethod();
        int testedFields = 0;

        for (Map.Entry<String, Object> sensitiveField : SENSITIVE_FIELDS.entrySet()) {
            String fieldName = sensitiveField.getKey();

            if (!existingFields.contains(fieldName) && !isFieldInPayload(data.getPayload(), fieldName)) {
                testMassAssignment(data, fieldName, sensitiveField.getValue());
                testedFields++;
            }
        }

        if (testedFields == 0) {
            logger.skip("All sensitive fields already exist in schema - nothing to test");
        } else {
            logger.info("Tested {} sensitive fields for mass assignment", testedFields);
        }
    }

    private boolean isFieldInPayload(String payload, String fieldName) {
        return payload.contains("\"" + fieldName + "\"");
    }

    private void testMassAssignment(FuzzingData data, String fieldName, Object fieldValue) {
        String fuzzedPayload = addFieldToPayload(data.getPayload(), fieldName, fieldValue);

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .fuzzingData(data)
                        .logger(logger)
                        .scenario("Add undeclared field [%s] with value [%s] to test mass assignment"
                                .formatted(fieldName, truncateValue(fieldValue)))
                        .fuzzer(this)
                        .payload(fuzzedPayload)
                        .responseProcessor((response, fuzzData) -> processResponse(response, fuzzData, fieldName, fieldValue))
                        .build()
        );
    }

    private String addFieldToPayload(String payload, String fieldName, Object fieldValue) {
        JsonElement jsonElement = JsonParser.parseString(payload);

        if (jsonElement instanceof JsonObject jsonObject) {
            addFieldToJsonObject(jsonObject, fieldName, fieldValue);
        } else if (jsonElement instanceof JsonArray jsonArray) {
            for (JsonElement element : jsonArray) {
                if (element instanceof JsonObject jsonObject) {
                    addFieldToJsonObject(jsonObject, fieldName, fieldValue);
                }
            }
        }

        return jsonElement.toString();
    }

    private void addFieldToJsonObject(JsonObject jsonObject, String fieldName, Object fieldValue) {
        switch (fieldValue) {
            case String s -> jsonObject.addProperty(fieldName, s);
            case Number number -> jsonObject.addProperty(fieldName, number);
            case Boolean b -> jsonObject.addProperty(fieldName, b);
            case List<?> list -> {
                JsonArray array = new JsonArray();
                for (Object item : list) {
                    switch (item) {
                        case String s -> array.add(s);
                        case Number number -> array.add(number);
                        case Boolean b -> array.add(b);
                        default -> array.add(item.toString());
                    }
                }
                jsonObject.add(fieldName, array);
            }
            default -> jsonObject.addProperty(fieldName, fieldValue.toString());
        }
    }

    private void processResponse(CatsResponse response, FuzzingData data, String fieldName, Object fieldValue) {
        String responseBody = response.getBody() != null ? response.getBody().toLowerCase(Locale.ROOT) : "";
        int responseCode = response.getResponseCode();
        boolean fieldReflected = isFieldReflectedInResponse(responseBody, fieldName, fieldValue);

        if (ResponseCodeFamily.is4xxCode(responseCode)) {
            testCaseListener.reportResultInfo(
                    logger, data, "Mass Assignment payload rejected",
                    "The API properly rejected the request with undeclared field [%s] (response code %d)"
                            .formatted(fieldName, responseCode));
            return;
        }

        if (fieldReflected && ResponseCodeFamily.is2xxCode(responseCode)) {
            testCaseListener.reportResultError(
                    logger, data, CatsResultFactory.Reason.MASS_ASSIGNMENT_DETECTED.value(),
                    "The undeclared field [%s] was accepted AND reflected in the response (code %d). The API is vulnerable to mass assignment attacks.".formatted(fieldName, responseCode));
            return;
        }

        if (ResponseCodeFamily.is2xxCode(responseCode)) {
            testCaseListener.reportResultError(
                    logger, data, CatsResultFactory.Reason.UNDECLARED_FIELD_ACCEPTED.value(),
                    "The API accepted the request with undeclared field [%s] (response code %d). APIs should reject requests with undeclared fields.".formatted(fieldName, responseCode));
            return;
        }

        if (ResponseCodeFamily.is5xxCode(responseCode)) {
            testCaseListener.reportResultError(
                    logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when processing undeclared field [%s]. This may indicate improper error handling.".formatted(responseCode, fieldName));
            return;
        }

        testCaseListener.reportResultError(
                logger, data, CatsResultFactory.Reason.UNEXPECTED_RESPONSE_CODE.value(),
                "Received unexpected response code %d for Mass Assignment payload".formatted(responseCode));
    }

    private boolean isFieldReflectedInResponse(String responseBody, String fieldName, Object fieldValue) {
        if (!responseBody.contains(fieldName.toLowerCase(Locale.ROOT))) {
            return false;
        }

        String valueStr = String.valueOf(fieldValue).toLowerCase(Locale.ROOT);
        return responseBody.contains(valueStr);
    }

    private String truncateValue(Object value) {
        String str = String.valueOf(value);
        return str.length() > 30 ? str.substring(0, 30) + "..." : str;
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "adds undeclared fields to the request payload to detect Mass Assignment vulnerabilities where APIs blindly bind user input";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
