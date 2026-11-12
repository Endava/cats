package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@QuarkusTest
class CommandCanaryTest {

    static Stream<String> canaryPayloads() {
        return Stream.of(
                CommandCanary.primaryShellArithmetic(),
                CommandCanary.primaryExprSemicolon(),
                CommandCanary.primaryExprPipe(),
                CommandCanary.primaryExprBackticks(),
                CommandCanary.primarySingleQuotedEcho(),
                CommandCanary.primaryWindowsSetArithmetic()
        );
    }

    @Test
    void shouldUseProductsWithinSigned32BitIntegerBounds() {
        long primary = (long) CommandCanary.PRIMARY_LEFT * CommandCanary.PRIMARY_RIGHT;
        long confirm = (long) CommandCanary.CONFIRM_LEFT * CommandCanary.CONFIRM_RIGHT;

        Assertions.assertThat(primary).isLessThanOrEqualTo(Integer.MAX_VALUE);
        Assertions.assertThat(confirm).isLessThanOrEqualTo(Integer.MAX_VALUE);
        Assertions.assertThat(Long.toString(primary)).isEqualTo(CommandCanary.PRIMARY_PRODUCT);
        Assertions.assertThat(Long.toString(confirm)).isEqualTo(CommandCanary.CONFIRM_PRODUCT);
    }

    @ParameterizedTest
    @MethodSource("canaryPayloads")
    void shouldNeverContainComputedProductInPayloadTextAndShouldRewriteToConfirmationPair(String payload) {
        Assertions.assertThat(CommandCanary.isPrimaryCanaryPayload(payload)).isTrue();
        Assertions.assertThat(payload).doesNotContain(CommandCanary.PRIMARY_PRODUCT, CommandCanary.CONFIRM_PRODUCT);

        String confirmation = CommandCanary.toConfirmationPayload(payload);
        Assertions.assertThat(confirmation)
                .contains(Integer.toString(CommandCanary.CONFIRM_LEFT), Integer.toString(CommandCanary.CONFIRM_RIGHT))
                .doesNotContain(Integer.toString(CommandCanary.PRIMARY_LEFT), CommandCanary.CONFIRM_PRODUCT);
    }

    @Test
    void shouldDetectStandalonePrimaryProductAndIgnoreLongerDigitSuperstrings() {
        CatsResponse exact = CatsResponse.builder().responseCode(200).body("{\"out\":\"" + CommandCanary.PRIMARY_PRODUCT + "\"}").build();
        CatsResponse embeddedInLonger = CatsResponse.builder().responseCode(200).body("{\"id\":\"9" + CommandCanary.PRIMARY_PRODUCT + "0\"}").build();
        CatsResponse echoedOperandsOnly = CatsResponse.builder().responseCode(200).body("{\"echo\":\"$((37199*43963))\"}").build();

        Assertions.assertThat(CommandCanary.containsPrimaryProduct(exact)).isTrue();
        Assertions.assertThat(CommandCanary.containsPrimaryProduct(embeddedInLonger)).isFalse();
        Assertions.assertThat(CommandCanary.containsPrimaryProduct(echoedOperandsOnly)).isFalse();
    }

    @Test
    void shouldMatchConfirmationProductOnlyWhenEvaluated() {
        String confirmPayload = CommandCanary.toConfirmationPayload(CommandCanary.primaryShellArithmetic());
        CatsResponse evaluated = CatsResponse.builder().responseCode(200).body("result=" + CommandCanary.CONFIRM_PRODUCT).build();
        CatsResponse stalePrimaryOnly = CatsResponse.builder().responseCode(200).body("result=" + CommandCanary.PRIMARY_PRODUCT).build();

        Assertions.assertThat(CommandCanary.matchesConfirmation(evaluated, confirmPayload)).isTrue();
        Assertions.assertThat(CommandCanary.matchesConfirmation(stalePrimaryOnly, confirmPayload)).isFalse();
    }
}
