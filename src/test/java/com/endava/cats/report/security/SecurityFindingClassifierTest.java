package com.endava.cats.report.security;

import com.endava.cats.model.FindingEvidence;
import com.endava.cats.model.SecurityFindingType;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class SecurityFindingClassifierTest {
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "BypassAuthentication,GET,200,DECLARED_AUTH_NOT_ENFORCED",
            "BypassAuthentication,PUT,204,ANONYMOUS_MODIFICATIONS",
            "BypassAuthentication,put,200,ANONYMOUS_MODIFICATIONS",
            "BypassAuthentication,NULL,200,DECLARED_AUTH_NOT_ENFORCED",
            "BypassAuthentication,GET,401,NULL",
            "HttpMethods,GET,200,HIDDEN_ENDPOINT",
            "CustomHttpMethods,GET,200,HIDDEN_ENDPOINT",
            "NonRestHttpMethods,GET,200,HIDDEN_ENDPOINT",
            "HttpMethods,GET,405,NULL",
            "InsecureDirectObjectReferences,GET,200,INCONSISTENT_WRITE_AUTHORIZATION",
            "HappyPath,GET,200,NULL"
    })
    void shouldRecognizeOnlyTheExistingSecurityFuzzerOutcomes(String fuzzer, String method, int status,
                                                              SecurityFindingType expected) {
        FindingEvidence evidence = new FindingEvidence(fuzzer, method, status, "Unexpected response code", null, "{}", "");

        Assertions.assertThat(SecurityFindingClassifier.fromFuzzer(evidence).orElse(null)).isEqualTo(expected);
    }
}
