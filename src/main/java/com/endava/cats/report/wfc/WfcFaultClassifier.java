package com.endava.cats.report.wfc;

import com.endava.cats.fuzzer.fields.AllFieldsAtMaxBoundaryFuzzer;
import com.endava.cats.fuzzer.fields.DefaultValuesInFieldsFuzzer;
import com.endava.cats.fuzzer.fields.ExamplesFieldsFuzzer;
import com.endava.cats.fuzzer.fields.IterateThroughEnumValuesFieldsFuzzer;
import com.endava.cats.fuzzer.fields.MaxLengthExactValuesInStringFieldsFuzzer;
import com.endava.cats.fuzzer.fields.MaximumExactNumbersInNumericFieldsFuzzer;
import com.endava.cats.fuzzer.fields.MinLengthExactValuesInStringFieldsFuzzer;
import com.endava.cats.fuzzer.fields.MinimumExactNumbersInNumericFieldsFuzzer;
import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.CheckDeletedResourcesNotAvailableFuzzer;
import com.endava.cats.fuzzer.http.CreatedResourceLocationFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.fuzzer.http.MisleadingCreatePutFuzzer;
import com.endava.cats.fuzzer.http.PutIdempotencyFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFlowFuzzer;
import com.endava.cats.fuzzer.special.RandomFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FindingEvidence;
import com.endava.cats.model.SecurityFindingType;
import com.endava.cats.report.security.SecurityFindingClassifier;
import com.endava.cats.report.error.extract.EmptyBody;
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
    private static final String EXPLICIT_PUBLIC_SECURITY_CONTEXT = "security: []";

    private static final List<Class<?>> POSITIVE_VALID_INPUT_FUZZER_CLASSES = List.of(
            HappyPathFuzzer.class,
            OnlyRequiredFieldsFuzzer.class,
            DefaultValuesInFieldsFuzzer.class,
            ExamplesFieldsFuzzer.class,
            IterateThroughEnumValuesFieldsFuzzer.class,
            MinLengthExactValuesInStringFieldsFuzzer.class,
            MaxLengthExactValuesInStringFieldsFuzzer.class,
            MinimumExactNumbersInNumericFieldsFuzzer.class,
            MaximumExactNumbersInNumericFieldsFuzzer.class,
            AllFieldsAtMaxBoundaryFuzzer.class,
            FunctionalFlowFuzzer.class,
            RandomFuzzer.class);

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
        map.put(Reason.MISSING_ALLOW_HEADER.value(), DefinedFaultCategory.HTTP_STATUS_NO_405_IF_NO_ALLOW);
        map.put(Reason.WRONG_ALLOW_HEADER.value(), DefinedFaultCategory.SCHEMA_INVALID_ALLOW);
        map.put(Reason.OPTIONS_ALLOW_MISMATCH.value(), DefinedFaultCategory.SCHEMA_INVALID_ALLOW);
        map.put(Reason.REJECTED_UPDATE_PARTIALLY_APPLIED.value(), DefinedFaultCategory.HTTP_SIDE_EFFECTS_FAILED_MODIFICATION);
        map.put(Reason.REPEATED_CREATE_PUT.value(), DefinedFaultCategory.HTTP_REPEATED_CREATE_PUT);
        map.put(Reason.MISLEADING_CREATE_PUT.value(), DefinedFaultCategory.HTTP_MISLEADING_CREATE_PUT);
        map.put(Reason.PARTIAL_UPDATE_PUT.value(), DefinedFaultCategory.HTTP_PARTIAL_UPDATE_PUT);
        map.put(Reason.FILTER_PARAMETER_IGNORED.value(), CatsWfcFaultCategory.CATS_FILTER_PARAMETER_IGNORED);
        map.put(Reason.PATCH_CLOBBERED_OMITTED_FIELDS.value(), DefinedFaultCategory.HTTP_INVALID_MERGE_PATCH);
        map.put(Reason.PAGINATION_LIMIT_IGNORED.value(), CatsWfcFaultCategory.CATS_PAGINATION_LIMIT_IGNORED);
        map.put(Reason.CONDITIONAL_GET_IGNORED.value(), CatsWfcFaultCategory.CATS_CONDITIONAL_GET_IGNORED);
        map.put(Reason.ARITHMETIC_TOTAL_MISMATCH_ACCEPTED.value(), CatsWfcFaultCategory.CATS_ARITHMETIC_TOTAL_MISMATCH_ACCEPTED);
        map.put(Reason.PATH_BODY_ID_MISMATCH_ACCEPTED.value(), CatsWfcFaultCategory.CATS_PATH_BODY_ID_MISMATCH_ACCEPTED);
        map.put(Reason.UNDECLARED_FIELD_ACCEPTED.value(), DefinedFaultCategory.SCHEMA_VALIDATION_BYPASS);
        map.put(Reason.READ_BROKEN_AFTER_FUZZING.value(), DefinedFaultCategory.HTTP_BROKEN_READ);
        map.put(Reason.UPDATE_READ_INCONSISTENCY.value(), DefinedFaultCategory.HTTP_UPDATE_READ_INCONSISTENCY);
        map.put(Reason.PUT_NOT_IDEMPOTENT.value(), DefinedFaultCategory.HTTP_NON_IDEMPOTENT_PUT);
        map.put(Reason.PUT_RETRY_REJECTED.value(), DefinedFaultCategory.HTTP_NON_IDEMPOTENT_PUT);
        map.put(Reason.CREATED_RESOURCE_UNAVAILABLE.value(), DefinedFaultCategory.HTTP_CREATED_RESOURCE_NOT_FOUND);
        map.put(Reason.STALE_IF_MATCH_ACCEPTED.value(), DefinedFaultCategory.HTTP_STALE_ETAG_ACCEPTED);
        return Map.copyOf(map);
    }

    /**
     * Classifies a {@link CatsTestCaseSummary} (optionally enriched with its full {@link CatsTestCase} details)
     * into zero or more WFC {@link FaultCategoryId} entries. Findings with {@link FindingConfidence#TENTATIVE}
     * confidence are not mapped through their reason, keeping fault counts comparable with tools that only
     * report verified findings.
     */
    public static Set<FaultCategoryId> classify(CatsTestCaseSummary summary, CatsTestCase fullTestCase) {
        return classify(summary, fullTestCase, OperationAuthContract.UNKNOWN);
    }

    /**
     * Classifies a {@link CatsTestCaseSummary} with its {@link OperationAuthContract} into zero or more WFC
     * {@link FaultCategoryId} entries, including passive schema-dependent auth faults (WFC 202 and 203).
     */
    public static Set<FaultCategoryId> classify(CatsTestCaseSummary summary, CatsTestCase fullTestCase,
                                                OperationAuthContract authContract) {
        if (summary == null) {
            return Set.of();
        }
        Set<FaultCategoryId> faults = new LinkedHashSet<>();
        int code = summary.getHttpResponseCode();
        String method = optionalUpper(summary.getHttpMethod());
        OperationAuthContract effectiveContract = authContract != null ? authContract : OperationAuthContract.UNKNOWN;

        classifyPassiveProtocolFaults(summary, fullTestCase, code, method, faults);
        classifyAuthSchemaFaults(code, effectiveContract, faults);
        if ((!summary.getError() && !summary.getWarning())
                || Reason.RATE_LIMITED.value().equals(summary.getResultReason())) {
            return Set.copyOf(faults);
        }

        findingFaults(FindingEvidence.from(summary)).stream().map(fault -> contextualFault(fault, summary)).forEach(faults::add);

        if (faults.isEmpty() && summary.getError() && !summary.is9xxResponse()) {
            faults.add(faultId(CatsWfcFaultCategory.CATS_UNEXPECTED_BEHAVIOUR, summary.getResultReason()));
        }
        return Set.copyOf(faults);
    }

    /** Returns the shared finding categories, without report-specific contexts or passive protocol checks. */
    public static Set<FaultCategory> categoriesOf(FindingEvidence evidence) {
        return findingFaults(evidence).stream().map(FindingFault::category).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<FindingFault> findingFaults(FindingEvidence evidence) {
        Set<FindingFault> faults = new LinkedHashSet<>();
        classifyStatusCodeFaults(evidence.responseCode(), faults);
        classifySecondPhaseFuzzers(evidence, evidence.responseCode(), faults);
        SecurityFindingClassifier.fromFuzzer(evidence).map(type -> fuzzerSecurityFault(evidence, type)).ifPresent(faults::add);
        if (evidence.confidence() != FindingConfidence.TENTATIVE) {
            classifyReasonBasedFaults(evidence, evidence.responseCode(), faults);
        }
        return Set.copyOf(faults);
    }

    private static FaultCategoryId contextualFault(FindingFault fault, CatsTestCaseSummary summary) {
        String context = fault.category() == DefinedFaultCategory.HTTP_STATUS_500 ? WfcFaultContext.of(summary) : fault.context();
        return faultId(fault.category(), context);
    }

    private record FindingFault(FaultCategory category, String context) {
    }

    private static void classifyAuthSchemaFaults(int code, OperationAuthContract contract, Set<FaultCategoryId> faults) {
        if (code == ResponseCodeFamily.STATUS_UNAUTHORIZED) {
            if (!contract.specHasSecuritySchemes()) {
                faults.add(faultId(DefinedFaultCategory.SCHEMA_STATUS_NO_401_IF_NO_AUTH, null));
            } else if (contract.explicitlyPublic()) {
                faults.add(faultId(DefinedFaultCategory.SCHEMA_STATUS_NO_401_IF_NO_AUTH, EXPLICIT_PUBLIC_SECURITY_CONTEXT));
            }
        } else if (code == ResponseCodeFamily.STATUS_FORBIDDEN && !contract.declares401()) {
            faults.add(faultId(DefinedFaultCategory.SCHEMA_STATUS_NO_403_IF_NO_401, null));
        }
    }

    private static void classifyStatusCodeFaults(int code, Set<FindingFault> faults) {
        if (ResponseCodeFamily.isInternalServerError(code)) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_STATUS_500, null));
        } else if (ResponseCodeFamily.isUnimplemented(code)) {
            faults.add(new FindingFault(DefinedFaultCategory.SCHEMA_STATUS_NO_501_IF_IMPLEMENTED, null));
        } else if (ResponseCodeFamily.isServerErrorOtherThan500And501(code)) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_OTHER_STATUS_5XX, HTTP_STATUS_CONTEXT_PREFIX + code));
        } else if (!ResponseCodeFamily.isValidHttpCode(code) && code / 100 != 9) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_STATUS_NO_NON_STANDARD_CODES, HTTP_STATUS_CONTEXT_PREFIX + code));
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
        boolean hasResponseBody = !EmptyBody.isEmpty(summary.getResponseBody());
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

    private static void classifySecondPhaseFuzzers(FindingEvidence evidence, int code, Set<FindingFault> faults) {
        String fuzzer = evidence.fuzzer();
        if (ConsoleUtils.matchesFuzzer(fuzzer, CheckDeletedResourcesNotAvailableFuzzer.class) && !ResponseCodeFamily.is5xxCode(code)) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_NONWORKING_DELETE, null));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, PutIdempotencyFuzzer.class) && code == ResponseCodeFamily.STATUS_CREATED) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_REPEATED_CREATE_PUT, fuzzer));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, MisleadingCreatePutFuzzer.class) && code == ResponseCodeFamily.STATUS_CREATED) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_MISLEADING_CREATE_PUT, fuzzer));
        } else if (ConsoleUtils.matchesFuzzer(fuzzer, CreatedResourceLocationFuzzer.class)
                && !ResponseCodeFamily.isNotFoundOrGone(code) && !ResponseCodeFamily.is5xxCode(code)) {
            faults.add(new FindingFault(DefinedFaultCategory.HTTP_INVALID_LOCATION, evidence.reason()));
        }
    }

    private static FindingFault fuzzerSecurityFault(FindingEvidence evidence, SecurityFindingType type) {
        String context = type == SecurityFindingType.INCONSISTENT_WRITE_AUTHORIZATION
                ? evidence.fuzzer() : optionalUpper(evidence.httpMethod());
        return new FindingFault(WfcSecurityMapping.categoryOf(type), context);
    }

    private static void classifyReasonBasedFaults(FindingEvidence evidence, int code, Set<FindingFault> faults) {
        String reason = evidence.reason();
        if (StringUtils.isBlank(reason)) {
            return;
        }
        FaultCategory exact = SecurityFindingType.fromReason(reason).map(WfcSecurityMapping::categoryOf)
                .orElseGet(() -> EXACT_REASON_MAP.get(reason.trim()));
        if (exact != null) {
            faults.add(new FindingFault(exact, evidence.fuzzer()));
            return;
        }
        if (reason.startsWith(Reason.UNEXPECTED_RESPONSE_CODE.value())
                || reason.startsWith(Reason.UNEXPECTED_BEHAVIOUR.value())) {
            classifyUnexpectedStatusCodeOracle(evidence, code, faults);
        }
    }

    private static void classifyUnexpectedStatusCodeOracle(FindingEvidence evidence, int code,
                                                           Set<FindingFault> faults) {
        String fuzzer = evidence.fuzzer();
        if (SecurityFindingClassifier.isUndocumentedMethodFuzzer(fuzzer)
                || ConsoleUtils.matchesFuzzer(fuzzer, CheckDeletedResourcesNotAvailableFuzzer.class)
                || ConsoleUtils.matchesFuzzer(fuzzer, BypassAuthenticationFuzzer.class)) {
            return;
        }
        boolean isPositiveFuzzer = ConsoleUtils.matchesAnyFuzzer(fuzzer, POSITIVE_VALID_INPUT_FUZZER_CLASSES);
        if (isPositiveFuzzer) {
            if (ResponseCodeFamily.isClientValidationError(code)) {
                faults.add(new FindingFault(DefinedFaultCategory.SCHEMA_VALID_INPUT_REJECTED, fuzzer));
            } else if (code == ResponseCodeFamily.STATUS_NOT_ACCEPTABLE) {
                faults.add(new FindingFault(DefinedFaultCategory.SCHEMA_STATUS_HAS_406_IF_ACCEPT, fuzzer));
            }
        } else if (ResponseCodeFamily.is2xxCode(code)) {
            faults.add(new FindingFault(DefinedFaultCategory.SCHEMA_VALIDATION_BYPASS, fuzzer));
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
