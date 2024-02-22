package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
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
class ZalgoTextInFieldsSanitizeValidateFuzzerTest {
    private ZalgoTextInFieldsSanitizeValidateFuzzer zalgoTextInFieldsSanitizeValidateFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        zalgoTextInFieldsSanitizeValidateFuzzer = new ZalgoTextInFieldsSanitizeValidateFuzzer(serviceCaller, testCaseListener, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldProperlyOverrideSuperClassMethods() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("field", new StringSchema());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        FuzzingStrategy fuzzingStrategy = zalgoTextInFieldsSanitizeValidateFuzzer.getFieldFuzzingStrategy(data, "field").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData().toString()).contains(" ̵̡̡̢̡̨̨̢͚̬̱̤̰̗͉͚̖͙͎͔͔̺̳͕̫̬͚̹͖̬̭̖̪̗͕̜̣̥̣̼͍͉̖͍̪͈̖͚̙͛͒͂̎̊̿̀̅̈͌͋̃̾̈̾̇͛͌͘͜͜͠͝ͅͅͅ ̷͕̗̇͛̅̀̑̇̈͗͌͛̐̀͆̐̊̅̋̈́̂̈́̈́͑̓͂͂̌̈́̽͌͐̐͂͐̈́̍̂͗̂͘͠͝͝͝ͅ ".replace(" ", ""));
        Assertions.assertThat(zalgoTextInFieldsSanitizeValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(zalgoTextInFieldsSanitizeValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(zalgoTextInFieldsSanitizeValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(zalgoTextInFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(null, "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(zalgoTextInFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(null, "pet#number")).isTrue();
    }
}
