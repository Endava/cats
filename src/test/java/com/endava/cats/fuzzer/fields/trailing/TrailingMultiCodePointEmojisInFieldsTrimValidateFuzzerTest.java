package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    @Mock
    private ServiceCaller serviceCaller;
    @Mock
    private TestCaseListener testCaseListener;
    @Mock
    private FilesArguments filesArguments;

    private TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzer trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer = new TrailingMultiCodePointEmojisInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        FuzzingStrategy fuzzingStrategy = trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(null, null).get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.trail().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsTrimValidateFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}
