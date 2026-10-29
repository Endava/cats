package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Set;

@QuarkusTest
class SensitiveDataInQueryLinterTest {
    private TestCaseListener testCaseListener;
    private SensitiveDataInQueryLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new SensitiveDataInQueryLinter(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"password", "api_key", "clientSecret", "access_token", "cardNumber", "cvv", "ssn"})
    void shouldReportWarnWhenSensitiveQueryParamPresent(String sensitiveParam) {
        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(HttpMethod.GET)
                .queryParams(Set.of(sensitiveParam, "limit"))
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Sensitive data exposed in URL query parameters"),
                Mockito.eq("The following URL query parameters appear to carry sensitive data: {}"),
                Mockito.eq(Set.of(sensitiveParam))
        );
    }

    @ParameterizedTest
    @CsvSource({"pageToken", "next_page_token", "continuationToken", "limit", "status", "userId"})
    void shouldReportInfoWhenSafeParamsOrPaginationTokensPresent(String safeParam) {
        FuzzingData data = FuzzingData.builder()
                .path("/users/{userId}")
                .method(HttpMethod.GET)
                .queryParams(Set.of(safeParam))
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("No sensitive credentials or PII found in query parameters")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("SensitiveDataInQueryLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("flags query parameters whose names indicate credentials, secrets, or sensitive PII (such as password, secret, apiKey, accessToken, ssn, cvv, cardNumber)");
    }
}
