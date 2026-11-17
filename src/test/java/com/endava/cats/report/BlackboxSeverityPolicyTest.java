package com.endava.cats.report;

import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FindingEvidence;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class BlackboxSeverityPolicyTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "SstiInjectionInStringFields|Potential SSTI vulnerability detected|CONFIRMED|true",
            "SstiInjectionInStringFields|Potential SSTI vulnerability detected|FIRM|false",
            "SstiInjectionInStringFields|Potential SSTI vulnerability detected|TENTATIVE|false",
            "SstiInjectionInStringFields|Potential SSTI vulnerability detected|NULL|false",
            "XxeInjectionInStringFields|XXE vulnerability detected|CONFIRMED|true",
            "XxeInjectionInStringFields|XXE vulnerability detected|FIRM|false",
            "XxeInjectionInStringFields|XXE vulnerability detected|TENTATIVE|false",
            "XxeInjectionInStringFields|XXE vulnerability detected|NULL|false",
            "PathTraversalInStringFields|Path traversal vulnerability detected|CONFIRMED|true",
            "PathTraversalInStringFields|Path traversal vulnerability detected|FIRM|false",
            "PathTraversalInStringFields|Path traversal vulnerability detected|TENTATIVE|false",
            "PathTraversalInStringFields|Path traversal vulnerability detected|NULL|false",
            "SstiInjectionInStringFields|Unrecognized finding|CONFIRMED|false",
            "XxeInjectionInStringFields|Unrecognized finding|CONFIRMED|false"
    })
    void shouldRecognizeConfirmedSecurityWithoutAStandardWfcCategory(String fuzzer, String reason,
                                                                    FindingConfidence confidence, boolean expected) {
        FindingEvidence evidence = new FindingEvidence(fuzzer, "POST", 200, reason, confidence, "{}", "Synthetic evidence");

        Assertions.assertThat(BlackboxSeverityPolicy.isReportable(evidence)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "AnyOracle|GET|500|SERVER_ERROR|NULL|true",
            "AnyOracle|GET|501|SERVER_ERROR|NULL|false",
            "AnyOracle|GET|501|SQL_INJECTION|CONFIRMED|false",
            "AnyOracle|GET|502|SERVER_ERROR|TENTATIVE|true",
            "BypassAuthentication|GET|200|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "BypassAuthentication|POST|201|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "BypassAuthentication|GET|401|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "HttpMethods|GET|200|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "SqlInjectionInStringFields|POST|200|SQL_INJECTION|CONFIRMED|true",
            "SqlInjectionInStringFields|POST|200|SQL_INJECTION|FIRM|false",
            "SqlInjectionInStringFields|POST|200|SQL_INJECTION|TENTATIVE|false",
            "SqlInjectionInStringFields|POST|200|SQL_INJECTION|NULL|false",
            "CheckSecurityHeaders|GET|200|MISSING_SECURITY_HEADERS|NULL|false",
            "CheckSecurityHeaders|GET|200|MISSING_SECURITY_HEADERS|CONFIRMED|false",
            "CrossParentResourceIsolation|GET|200|CROSS_PARENT_RESOURCE_EXPOSURE|NULL|false",
            "PutIdempotency|PUT|200|PUT_NOT_IDEMPOTENT|NULL|false",
            "AnyOracle|GET|302|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "AnyOracle|GET|101|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "Template|GET|200|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "Template|GET|500|UNEXPECTED_RESPONSE_CODE|NULL|true",
            "Template|GET|501|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "Random|GET|200|UNEXPECTED_RESPONSE_CODE|NULL|false",
            "Random|GET|500|UNEXPECTED_RESPONSE_CODE|NULL|true",
            "Random|GET|502|UNEXPECTED_RESPONSE_CODE|NULL|true",
            "AnyOracle|GET|400|ERROR_LEAKS_DETECTED|NULL|false",
            "AnyOracle|GET|200|CALL_TIMEOUT_EXCEEDED|NULL|false",
            "AnyOracle|GET|200|NOT_MATCHING_RESPONSE_SCHEMA|NULL|false",
            "MassAssignment|POST|200|MASS_ASSIGNMENT_DETECTED|NULL|false",
            "AnyOracle|GET|200|SENSITIVE_DATA_EXPOSED|NULL|false",
            "InsecureDirectObjectReferences|GET|200|POTENTIAL_IDOR|NULL|false",
            "HttpMethodOverrideTunneling|POST|204|HTTP_METHOD_OVERRIDE_ACCEPTED|NULL|false",
            "AnyOracle|GET|400|ERROR_LEAKS_DETECTED|CONFIRMED|true",
            "BypassAuthentication|GET|200|UNEXPECTED_RESPONSE_CODE|CONFIRMED|true",
            "CrossParentResourceIsolation|GET|200|CROSS_PARENT_RESOURCE_EXPOSURE|CONFIRMED|true",
            "MassAssignment|POST|200|MASS_ASSIGNMENT_DETECTED|CONFIRMED|true"
    })
    void shouldKeepOnlyCrashesAndTrustworthyHighSeverityFindings(String fuzzer, String method, int status, Reason reason,
                                                               FindingConfidence confidence, boolean expected) {
        FindingEvidence evidence = new FindingEvidence(fuzzer, method, status, reason.value(), confidence, "{}", "Synthetic evidence");

        Assertions.assertThat(BlackboxSeverityPolicy.isReportable(evidence)).isEqualTo(expected);
    }
}
