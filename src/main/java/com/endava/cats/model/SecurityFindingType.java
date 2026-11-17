package com.endava.cats.model;

import com.endava.cats.model.CatsResultFactory.Reason;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** CATS security semantics, independent of the vocabulary used by an exported report. */
public enum SecurityFindingType {
    SQL_INJECTION(true, Reason.SQL_INJECTION.value()),
    XSS_INJECTION(true, Reason.XSS_INJECTION.value(), "XSS injection vulnerability detected"),
    SSRF(true, Reason.SSRF_REFLECTED.value(), Reason.INTERNAL_TARGET_REFLECTED_SSRF.value(),
            Reason.CLOUD_METADATA_REFLECTED_SSRF.value(), Reason.FILE_CONTENT_EXPOSED_SSRF.value(),
            Reason.NETWORK_ERROR_SSRF.value(), Reason.DNS_RESOLUTION_SSRF.value(), Reason.HTTP_CLIENT_SSRF.value()),
    MASS_ASSIGNMENT(true, Reason.MASS_ASSIGNMENT_DETECTED.value()),
    INCONSISTENT_WRITE_AUTHORIZATION(true, Reason.POTENTIAL_IDOR.value(), Reason.CROSS_PARENT_RESOURCE_EXPOSURE.value()),
    ANONYMOUS_MODIFICATIONS(true),
    LEAKED_STACK_TRACE(true, Reason.ERROR_LEAKS_DETECTED.value()),
    HIDDEN_ENDPOINT(true, Reason.HTTP_METHOD_OVERRIDE_ACCEPTED.value()),
    DECLARED_AUTH_NOT_ENFORCED(true),
    CALL_TIMEOUT(false, Reason.CALL_TIMEOUT_EXCEEDED.value(), Reason.RESPONSE_TIME_EXCEEDS_MAX.value()),
    COMMAND_INJECTION(true, Reason.COMMAND_INJECTION.value(), Reason.POSSIBLE_COMMAND_INJECTION.value()),
    NOSQL_INJECTION(true, Reason.NOSQL_INJECTION.value()),
    EXCESSIVE_DATA_EXPOSURE(true, Reason.WRITE_ONLY_FIELDS_EXPOSED.value(), Reason.SENSITIVE_DATA_EXPOSED.value()),
    MISSING_SECURITY_HEADERS(false, Reason.MISSING_SECURITY_HEADERS.value()),
    LDAP_INJECTION(true, Reason.LDAP_INJECTION.value(), Reason.POSSIBLE_LDAP_INJECTION.value(),
            Reason.LDAP_DATA_EXTRACTION.value(), Reason.LDAP_AUTHENTICATION_BYPASS.value()),
    PATH_TRAVERSAL(true, Reason.PATH_TRAVERSAL.value(), "PathTraversal injection vulnerability detected"),
    SSTI(true, "Potential SSTI vulnerability detected", "SSTI injection error detected",
            "Potential SSTI reflection detected", "SSTI injection vulnerability detected"),
    XXE(true, "XXE vulnerability detected", "Potential XXE vulnerability detected",
            "Potential XXE DoS vulnerability detected", "XXE injection vulnerability detected");

    private static final Map<String, SecurityFindingType> BY_REASON = Arrays.stream(values())
            .flatMap(type -> type.reasons.stream().map(reason -> Map.entry(reason, type)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private final boolean highSeverity;
    private final Set<String> reasons;

    SecurityFindingType(boolean highSeverity, String... reasons) {
        this.highSeverity = highSeverity;
        this.reasons = Set.of(reasons);
    }

    public boolean isHighSeverity() {
        return highSeverity;
    }

    /** Includes existing detector titles without rewriting persisted reasons or problem identities. */
    public static Optional<SecurityFindingType> fromReason(String reason) {
        return Optional.ofNullable(reason).map(String::trim).map(BY_REASON::get);
    }
}
