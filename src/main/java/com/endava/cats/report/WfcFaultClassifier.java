package com.endava.cats.report;

import com.endava.cats.fuzzer.fields.DefaultValuesInFieldsFuzzer;
import com.endava.cats.fuzzer.fields.ExamplesFieldsFuzzer;
import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.CheckDeletedResourcesNotAvailableFuzzer;
import com.endava.cats.fuzzer.http.ConditionalEtagFuzzer;
import com.endava.cats.fuzzer.http.CreatedResourceLocationFuzzer;
import com.endava.cats.fuzzer.http.CrossParentResourceIsolationFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.fuzzer.http.InsecureDirectObjectReferencesFuzzer;
import com.endava.cats.fuzzer.http.PutIdempotencyFuzzer;
import com.endava.cats.fuzzer.http.UpdateReadConsistencyFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFuzzer;
import com.endava.cats.fuzzer.special.RandomFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.KeyValuePair;
import com.google.common.net.HttpHeaders;
import com.webfuzzing.commons.faults.DefinedFaultCategory;
import com.webfuzzing.commons.faults.FaultCategory;
import com.webfuzzing.commons.report.FaultCategoryId;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Classifies CATS test case outcomes into standardized Web Fuzzing Commons ({@link DefinedFaultCategory})
 * and CATS-specific ({@link CatsWfcFaultCategory}) fault category identifiers.
 */
public final class WfcFaultClassifier {
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final String HTTP_STATUS_CONTEXT_PREFIX = "HTTP ";

    private static final List<Class<?>> POSITIVE_VALID_INPUT_FUZZER_CLASSES = List.of(
            HappyPathFuzzer.class,
            OnlyRequiredFieldsFuzzer.class,
            DefaultValuesInFieldsFuzzer.class,
            ExamplesFieldsFuzzer.class,
            FunctionalFuzzer.class,
            RandomFuzzer.class);

    private static final Set<String> SSRF_REASONS = Set.of(
            Reason.SSRF_REFLECTED.value(),
            Reason.INTERNAL_TARGET_REFLECTED_SSRF.value(),
            Reason.CLOUD_METADATA_REFLECTED_SSRF.value(),
            Reason.FILE_CONTENT_EXPOSED_SSRF.value(),
            Reason.NETWORK_ERROR_SSRF.value(),
            Reason.DNS_RESOLUTION_SSRF.value(),
            Reason.HTTP_CLIENT_SSRF.value());

    private static final Map<String, FaultCategory> EXACT_REASON_MAP = buildExactReasonMap();

    private WfcFaultClassifier() {
        // Utility class
    }

    private static Map<String, FaultCategory> buildExactReasonMap() {
        Map<String, FaultCategory> map = new LinkedHashMap<>();
        map.put(Reason.NOT_MATCHING_RESPONSE_SCHEMA.value(), DefinedFaultCategory.SCHEMA_INVALID_RESPONSE);
        map.put(Reason.RESPONSE_CONTENT_TYPE_NOT_MATCHING.value(), DefinedFaultCategory.SCHEMA_INVALID_RESPONSE);
        map.put(Reason.MISSING_RESPONSE_HEADERS.value(), DefinedFaultCategory.SCHEMA_INVALID_RESPONSE);
        map.put(Reason.NOT_IMPLEMENTED.value(), DefinedFaultCategory.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED);
        map.put(Reason.NOT_IMPLEMENTED.description(), DefinedFaultCategory.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED);
        map.put(Reason.SQL_INJECTION.value(), DefinedFaultCategory.SECURITY_SQL_INJECTION);
        map.put(Reason.XSS_INJECTION.value(), DefinedFaultCategory.SECURITY_XSS_INJECTION);
        map.put(Reason.MASS_ASSIGNMENT_DETECTED.value(), DefinedFaultCategory.SECURITY_MASS_ASSIGNMENT);
        map.put(Reason.UNDECLARED_FIELD_ACCEPTED.value(), DefinedFaultCategory.SCHEMA_VALIDATION_BYPASS);
        map.put(Reason.ERROR_LEAKS_DETECTED.value(), DefinedFaultCategory.SECURITY_LEAKED_STACK_TRACES);
        map.put(Reason.CALL_TIMEOUT_EXCEEDED.value(), DefinedFaultCategory.SECURITY_CALL_TIMEOUT);
        map.put(Reason.RESPONSE_TIME_EXCEEDS_MAX.value(), DefinedFaultCategory.SECURITY_CALL_TIMEOUT);
        map.put(Reason.POTENTIAL_IDOR.value(), DefinedFaultCategory.SECURITY_INCONSISTENT_WRITE_AUTHORIZATION);
        map.put(Reason.COMMAND_INJECTION.value(), CatsWfcFaultCategory.CATS_COMMAND_INJECTION);
        map.put(Reason.POSSIBLE_COMMAND_INJECTION.value(), CatsWfcFaultCategory.CATS_COMMAND_INJECTION);
        map.put(Reason.NOSQL_INJECTION.value(), CatsWfcFaultCategory.CATS_NOSQL_INJECTION);
        map.put(Reason.WRITE_ONLY_FIELDS_EXPOSED.value(), CatsWfcFaultCategory.CATS_SENSITIVE_DATA_EXPOSURE);
        map.put(Reason.SENSITIVE_DATA_EXPOSED.value(), CatsWfcFaultCategory.CATS_SENSITIVE_DATA_EXPOSURE);
        map.put(Reason.MISSING_SECURITY_HEADERS.value(), CatsWfcFaultCategory.CATS_MISSING_SECURITY_HEADERS);
        map.put(Reason.READ_BROKEN_AFTER_FUZZING.value(), CatsWfcFaultCategory.CATS_READ_BROKEN_AFTER_FUZZING);
        SSRF_REASONS.forEach(reason -> map.put(reason, DefinedFaultCategory.SECURITY_SSRF));
        return Map.copyOf(map);
    }

    /**
     * Classifies a {@link CatsTestCaseSummary} (optionally enriched with its full {@link CatsTestCase} details)
     * into zero or more WFC {@link FaultCategoryId} entries.
     */
    public static Set<FaultCategoryId> classify(CatsTestCaseSummary summary, CatsTestCase fullTestCase) {
        if (summary == null) {
            return Set.of();
        }
        Set<FaultCategoryId> faults = new LinkedHashSet<>();
        int code = summary.getHttpResponseCode();
        String method = optionalUpper(summary.getHttpMethod());

        classifyPassiveProtocolFaults(summary, fullTestCase, code, method, faults);
        if (!summary.getError() && !summary.getWarning()) {
            return faults;
        }

        classifyStatusCodeFaults(summary, code, faults);
        classifySecondPhaseAndSecurityFuzzers(summary, code, method, faults);
        classifyReasonBasedFaults(summary, code, faults);

        if (faults.isEmpty() && summary.getError() && !summary.is9xxResponse()) {
            faults.add(faultId(CatsWfcFaultCategory.CATS_UNEXPECTED_BEHAVIOUR, summary.getResultReason()));
        }
        return Set.copyOf(faults);
    }

    private static void classifyStatusCodeFaults(CatsTestCaseSummary summary, int code, Set<FaultCategoryId> faults) {
        if (ResponseCodeFamily.isInternalServerError(code)) {
            String context = ErrorSimilarityDetector.extractRootCauseSummary(
                    summary.getResponseBody(), summary.getResultDetails());
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_500, context));
        } else if (ResponseCodeFamily.isUnimplemented(code)) {
            faults.add(faultId(DefinedFaultCategory.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED, null));
        } else if (ResponseCodeFamily.isServerErrorOtherThan500And501(code)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_OTHER_STATUS_5XX, HTTP_STATUS_CONTEXT_PREFIX + code));
        } else if (!ResponseCodeFamily.isValidHttpCode(code) && !summary.is9xxResponse()) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_NON_STANDARD_CODES, HTTP_STATUS_CONTEXT_PREFIX + code));
        }
    }

    private static void classifyPassiveProtocolFaults(CatsTestCaseSummary summary, CatsTestCase fullTestCase,
                                                      int code, String method, Set<FaultCategoryId> faults) {
        if (code == ResponseCodeFamily.STATUS_CREATED) {
            if (HttpMethod.DELETE.matches(method)) {
                faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_201_IF_DELETE, null));
            } else if (HttpMethod.GET.matches(method)) {
                faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_201_IF_GET, null));
            } else if (HttpMethod.PATCH.matches(method)) {
                faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_201_IF_PATCH, null));
            }
        }
        boolean hasResponseBody = !ErrorSimilarityDetector.isBlankOrEmptyPlaceholder(summary.getResponseBody());
        if (code == ResponseCodeFamily.STATUS_NO_CONTENT && hasResponseBody) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_204_IF_CONTENT, null));
        } else if (code == ResponseCodeFamily.STATUS_RESET_CONTENT && hasResponseBody) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_205_IF_CONTENT, null));
        }
        if (code == ResponseCodeFamily.STATUS_NOT_MODIFIED && !HttpMethod.isReadOnlyMethod(method)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_304_IF_NO_GET_OR_HEAD, method));
        }
        if (fullTestCase != null) {
            classifyHeaderAndPayloadProtocolFaults(fullTestCase, code, faults);
        }
    }

    private static void classifyHeaderAndPayloadProtocolFaults(CatsTestCase fullTestCase, int code,
                                                               Set<FaultCategoryId> faults) {
        boolean hasReqPayload = fullTestCase.getRequest() != null
                && StringUtils.isNotBlank(fullTestCase.getRequest().getPayload())
                && !EMPTY_JSON_OBJECT.equals(fullTestCase.getRequest().getPayload().trim());
        if (code == ResponseCodeFamily.STATUS_PAYLOAD_TOO_LARGE && !hasReqPayload) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_413_IF_NO_PAYLOAD, null));
        } else if (code == ResponseCodeFamily.STATUS_UNSUPPORTED_MEDIA_TYPE && !hasReqPayload) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_415_IF_NO_PAYLOAD, null));
        }
        List<KeyValuePair<String, String>> headers = fullTestCase.getResponse() != null
                ? fullTestCase.getResponse().getHeaders() : null;
        if (headers == null || headers.isEmpty()) {
            return;
        }
        if (code == ResponseCodeFamily.STATUS_UNAUTHORIZED && !containsHeader(headers, HttpHeaders.WWW_AUTHENTICATE)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_401_IF_NO_WWW_AUTHENTICATE, null));
        } else if (code == ResponseCodeFamily.STATUS_METHOD_NOT_ALLOWED && !containsHeader(headers, HttpHeaders.ALLOW)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_405_IF_NO_ALLOW, null));
        } else if (code == ResponseCodeFamily.STATUS_UPGRADE_REQUIRED && !containsHeader(headers, HttpHeaders.UPGRADE)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_STATUS_NO_426_IF_NO_UPGRADE, null));
        }
    }

    private static boolean containsHeader(List<KeyValuePair<String, String>> headers, String target) {
        return headers.stream().anyMatch(h -> h.getKey() != null && h.getKey().equalsIgnoreCase(target));
    }

    private static void classifySecondPhaseAndSecurityFuzzers(CatsTestCaseSummary summary, int code, String method,
                                                              Set<FaultCategoryId> faults) {
        String fuzzer = summary.getFuzzer();
        boolean is2xx = ResponseCodeFamily.is2xxCode(code);
        if (ConsoleUtils.matchesFuzzer(fuzzer, CheckDeletedResourcesNotAvailableFuzzer.class)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_NONWORKING_DELETE, null));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, PutIdempotencyFuzzer.class)) {
            faults.add(faultId(DefinedFaultCategory.HTTP_NON_IDEMPOTENT_PUT, summary.getResultReason()));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, CreatedResourceLocationFuzzer.class)) {
            FaultCategory cat = ResponseCodeFamily.isNotFoundOrGone(code)
                    ? DefinedFaultCategory.HTTP_CREATED_RESOURCE_NOT_FOUND
                    : DefinedFaultCategory.HTTP_INVALID_LOCATION;
            faults.add(faultId(cat, summary.getResultReason()));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, UpdateReadConsistencyFuzzer.class)) {
            faults.add(faultId(CatsWfcFaultCategory.CATS_UPDATE_READ_INCONSISTENCY, summary.getResultReason()));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, ConditionalEtagFuzzer.class) && is2xx) {
            faults.add(faultId(CatsWfcFaultCategory.CATS_STALE_ETAG_ACCEPTED, null));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, BypassAuthenticationFuzzer.class) && is2xx) {
            FaultCategory cat = HttpMethod.isResourceModificationMethod(method)
                    ? DefinedFaultCategory.SECURITY_ANONYMOUS_MODIFICATIONS
                    : DefinedFaultCategory.SECURITY_DECLARED_AUTH_NOT_ENFORCED;
            faults.add(faultId(cat, method));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, CrossParentResourceIsolationFuzzer.class)
                || ConsoleUtils.matchesFuzzer(fuzzer, InsecureDirectObjectReferencesFuzzer.class)) {
            faults.add(faultId(DefinedFaultCategory.SECURITY_INCONSISTENT_WRITE_AUTHORIZATION, summary.getFuzzer()));
        }
    }

    private static void classifyReasonBasedFaults(CatsTestCaseSummary summary, int code, Set<FaultCategoryId> faults) {
        String reason = summary.getResultReason();
        if (StringUtils.isBlank(reason)) {
            return;
        }
        FaultCategory exact = EXACT_REASON_MAP.get(reason.trim());
        if (exact != null) {
            faults.add(faultId(exact, summary.getFuzzer()));
            return;
        }
        if (reason.startsWith(Reason.UNEXPECTED_RESPONSE_CODE.value())
                || reason.startsWith(Reason.UNDOCUMENTED_RESPONSE_CODE.value())
                || reason.startsWith(Reason.UNEXPECTED_BEHAVIOUR.value())) {
            classifyUnexpectedStatusCodeOracle(summary, code, faults);
        }
    }

    private static void classifyUnexpectedStatusCodeOracle(CatsTestCaseSummary summary, int code,
                                                           Set<FaultCategoryId> faults) {
        boolean isPositiveFuzzer = ConsoleUtils.matchesAnyFuzzer(summary.getFuzzer(), POSITIVE_VALID_INPUT_FUZZER_CLASSES);
        if (isPositiveFuzzer) {
            if (ResponseCodeFamily.isClientValidationError(code)) {
                faults.add(faultId(DefinedFaultCategory.SCHEMA_VALID_INPUT_REJECTED, summary.getFuzzer()));
            } else if (code == ResponseCodeFamily.STATUS_NOT_ACCEPTABLE) {
                faults.add(faultId(DefinedFaultCategory.SCHEMA_STATUS_HAS_406_IF_ACCEPT, summary.getFuzzer()));
            }
        } else if (ResponseCodeFamily.is2xxCode(code)) {
            faults.add(faultId(DefinedFaultCategory.SCHEMA_VALIDATION_BYPASS, summary.getFuzzer()));
        }
    }

    public static FaultCategoryId faultId(FaultCategory category, String context) {
        FaultCategoryId id = new FaultCategoryId();
        id.setId(category.getId());
        id.setCode(category.getCode());
        if (StringUtils.isNotBlank(context)) {
            id.setContext(context);
        }
        return id;
    }

    private static String optionalUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
