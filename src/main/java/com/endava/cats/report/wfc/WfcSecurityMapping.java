package com.endava.cats.report.wfc;

import com.endava.cats.model.SecurityFindingType;
import com.webfuzzing.commons.faults.DefinedFaultCategory;
import com.webfuzzing.commons.faults.FaultCategory;

/** Adapts each native security type to a standard category, or a CATS category where WFC has none. */
final class WfcSecurityMapping {
    private WfcSecurityMapping() {
    }

    static FaultCategory categoryOf(SecurityFindingType type) {
        return switch (type) {
            case SQL_INJECTION -> DefinedFaultCategory.SECURITY_SQL_INJECTION;
            case XSS_INJECTION -> DefinedFaultCategory.SECURITY_XSS_INJECTION;
            case SSRF -> DefinedFaultCategory.SECURITY_SSRF;
            case MASS_ASSIGNMENT -> DefinedFaultCategory.SECURITY_MASS_ASSIGNMENT;
            case INCONSISTENT_WRITE_AUTHORIZATION -> DefinedFaultCategory.SECURITY_INCONSISTENT_WRITE_AUTHORIZATION;
            case ANONYMOUS_MODIFICATIONS -> DefinedFaultCategory.SECURITY_ANONYMOUS_MODIFICATIONS;
            case LEAKED_STACK_TRACE -> DefinedFaultCategory.SECURITY_LEAKED_STACK_TRACES;
            case HIDDEN_ENDPOINT -> DefinedFaultCategory.SECURITY_HIDDEN_ACCESSIBLE_ENDPOINT;
            case DECLARED_AUTH_NOT_ENFORCED -> DefinedFaultCategory.SECURITY_DECLARED_AUTH_NOT_ENFORCED;
            case CALL_TIMEOUT -> DefinedFaultCategory.SECURITY_CALL_TIMEOUT;
            case COMMAND_INJECTION -> DefinedFaultCategory.SECURITY_COMMAND_INJECTION;
            case NOSQL_INJECTION -> DefinedFaultCategory.SECURITY_NOSQL_INJECTION;
            case EXCESSIVE_DATA_EXPOSURE -> DefinedFaultCategory.SECURITY_EXCESSIVE_DATA_EXPOSURE;
            case MISSING_SECURITY_HEADERS -> DefinedFaultCategory.SECURITY_MISSING_HEADERS;
            case LDAP_INJECTION -> DefinedFaultCategory.SECURITY_LDAP_INJECTION;
            case SSTI -> CatsWfcFaultCategory.CATS_SSTI;
            case XXE -> CatsWfcFaultCategory.CATS_XXE;
            case PATH_TRAVERSAL -> CatsWfcFaultCategory.CATS_PATH_TRAVERSAL;
        };
    }
}
