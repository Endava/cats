package com.endava.cats.fuzzer.executor;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.ExpectedResponse;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SimpleExecutorContextTest {
    @Test
    void shouldRecordTheSpecificDeclarationWithoutChangingTheExecutionFamily() {
        SimpleExecutorContext context = SimpleExecutorContext.builder().fuzzingData(FuzzingData.builder().build())
                .expectedSpecificResponseCode("405").build();

        Assertions.assertThat(context.getExpectedResponseForReporting().codes()).containsExactly("405");
        Assertions.assertThat(context.getExpectedResponseForReporting().outcome()).isEqualTo(ExpectedResponse.Outcome.REJECTION);
        Assertions.assertThat(context.getExpectedResponseCode()).isNull();
    }

    @Test
    void shouldPreferTheExecutionFamilyOverAMoreSpecificDisplayLabel() {
        SimpleExecutorContext context = SimpleExecutorContext.builder().fuzzingData(FuzzingData.builder().build())
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX).expectedSpecificResponseCode("405").build();

        Assertions.assertThat(context.getExpectedResponseForReporting().codes()).containsExactlyElementsOf(ResponseCodeFamilyPredefined.FOURXX.allowedResponseCodes());
        Assertions.assertThat(context.getExpectedSpecificResponseCode()).isEqualTo("405");
    }
}
