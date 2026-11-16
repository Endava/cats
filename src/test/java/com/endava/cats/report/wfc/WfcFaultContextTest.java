package com.endava.cats.report.wfc;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class WfcFaultContextTest {

    @Test
    void shouldKeepHistoricalWfcFaultIdentitiesForRootStringPayloads() {
        String first = contextOf("{\"message\":\"Cannot deserialize from String value ('TOKEN_ALPHA')\"}", "\"TOKEN_ALPHA\"");
        String second = contextOf("{\"message\":\"Cannot deserialize from String value ('TOKEN_BETA')\"}", "\"TOKEN_BETA\"");

        Assertions.assertThat(first).contains("TOKEN_ALPHA").doesNotContain("<INPUT>").isNotEqualTo(second);
    }

    @Test
    void shouldNotUseAProblemOnlySnapshotWhenWfcNeedsItsHistoricalVocabulary() {
        CatsTestCase test = new CatsTestCase();
        String body = "{\"message\":\"Cannot deserialize from String value ('TOKEN_ALPHA')\"}";
        test.setRequest(CatsRequest.builder().httpMethod("POST").payload("\"TOKEN_ALPHA\"").build());
        test.setResponse(CatsResponse.builder().responseCode(500).body(body).build());
        test.setResult("error");
        CatsTestCaseSummary summary = CatsTestCaseSummary.fromCatsTestCase(test);
        summary.attachErrorSignature(ErrorSignatureMasker.signatureOf(body, null, summary.getRequestVocabulary()));

        Assertions.assertThat(WfcFaultContext.of(summary)).contains("TOKEN_ALPHA").doesNotContain("<INPUT>");
    }

    @Test
    void shouldKeepOneContextForTheSameCrashWhateverTheEchoedValuesAndIds() {
        String first = contextOf("{\"error\":\"Internal Server Error\",\"message\":\"Order 42 failed at 2026-11-13T10:00:00Z\",\"id\":\"c1f5c992-9532-43cb-b33a-d592f504bc15\"}", "{\"order\":\"abc-1\"}");
        String second = contextOf("{\"error\":\"Internal Server Error\",\"message\":\"Order 77 failed at 2026-11-14T11:30:00Z\",\"id\":\"0a1b2c3d-1111-2222-3333-444455556666\"}", "{\"order\":\"xyz-9\"}");

        Assertions.assertThat(first).isEqualTo(second).isEqualTo("Internal Server Error: Order NUM failed at TIMESTAMP");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"message\":\"Cannot read properties of undefined (reading 'id')\"} | {\"message\":\"Cannot read properties of undefined (reading 'name')\"}",
            "{\"code\":\"USR-001\",\"message\":\"Boom\"} | {\"code\":\"USR-002\",\"message\":\"Boom\"}",
            "{\"title\":\"NPE\",\"trace\":\"at com.acme.A.run(A.java:1)\"} | {\"title\":\"NPE\",\"trace\":\"at com.acme.B.run(B.java:2)\"}"
    })
    void shouldTellDifferentCrashesApart(String firstBody, String secondBody) {
        Assertions.assertThat(contextOf(firstBody, null)).isNotEqualTo(contextOf(secondBody, null));
    }

    @Test
    void shouldTellPythonCrashSitesApartThroughTheirFrames() {
        String first = contextOf("Traceback (most recent call last):\n  File \"/usr/lib/python3/site-packages/starlette/routing.py\", line 1, in f\n  File \"/app/a.py\", line 10, in load_user\nKeyError: 'name'", null);
        String second = contextOf("Traceback (most recent call last):\n  File \"/usr/lib/python3/site-packages/starlette/routing.py\", line 1, in f\n  File \"/app/b.py\", line 20, in save_item\nKeyError: 'name'", null);

        Assertions.assertThat(first).contains("load_user@/app/a.py:10").isNotEqualTo(second);
    }

    @Test
    void shouldDescribeEmptyBodies() {
        Assertions.assertThat(contextOf("", null)).startsWith("[Empty response body]");
    }

    private static String contextOf(String body, String payload) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(CatsRequest.builder().httpMethod("POST").payload(payload).build());
        testCase.setResponse(CatsResponse.builder().responseCode(500).body(body).build());
        testCase.setResult("error");
        testCase.setContractPath("/orders");
        return WfcFaultContext.of(CatsTestCaseSummary.fromCatsTestCase(testCase));
    }
}
