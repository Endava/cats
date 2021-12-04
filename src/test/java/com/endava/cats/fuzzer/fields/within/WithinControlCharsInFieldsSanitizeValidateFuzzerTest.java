package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
class WithinControlCharsInFieldsSanitizeValidateFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;

    private WithinControlCharsInFieldsSanitizeValidateFuzzer withinControlCharsInFieldsSanitizeValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        withinControlCharsInFieldsSanitizeValidateFuzzer = new WithinControlCharsInFieldsSanitizeValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void shouldProperlyOverrideSuperClassMethods() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("field", new StringSchema());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        FuzzingStrategy fuzzingStrategy = withinControlCharsInFieldsSanitizeValidateFuzzer.getFieldFuzzingStrategy(data, "field").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());

        Assertions.assertThat(fuzzingStrategy.getData()).contains("\u0007");
        Assertions.assertThat(withinControlCharsInFieldsSanitizeValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(withinControlCharsInFieldsSanitizeValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(withinControlCharsInFieldsSanitizeValidateFuzzer.concreteFuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());

        Assertions.assertThat(withinControlCharsInFieldsSanitizeValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(withinControlCharsInFieldsSanitizeValidateFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(withinControlCharsInFieldsSanitizeValidateFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}
