package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
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
class ZalgoTextInStringFieldsValidateSanitizeFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ZalgoTextInStringFieldsValidateSanitizeFuzzer zalgoTextInStringFieldsValidateSanitizeFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        zalgoTextInStringFieldsValidateSanitizeFuzzer = new ZalgoTextInStringFieldsValidateSanitizeFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldProperlyOverrideSuperClassMethods() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("field", new StringSchema());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        FuzzingStrategy fuzzingStrategy = zalgoTextInStringFieldsValidateSanitizeFuzzer.getFieldFuzzingStrategy(data, "field").get(0);

        Assertions.assertThat(fuzzingStrategy.getData()).contains("c̷̨̛̥̬͉̘̬̻̩͕͚̦̺̻͓̳͇̲̭̝̙̟̈́̉̐͂͒̆͂̿͌̑͐̌̇̈́̾̉̆̀̅̓͛͋̈̄͊̈̄̎̃̒͂̓̊̌̎̌̃́̅͊̏͘͘͘̕̕͘͠͝a̶͖̐͆͑́͆̓͗͆̏̑̈́̾͛̎̂̒̄̏̍͌͛̀́̄̓̍̐͂̀́̈́̂͐̕̕̕̚͘͠͝͠t̵̨̢̨͙̪̼͚͖̲̻̞̦̤̲̖͚̟̯͔̬̜̬͖̺͎̼̬̞̱̳͚͔͎̩̩̩̲̗̩̊̽̈́̔̀̍͒̓̂͐̾̆̐̒̄͂͒̽̾̔͊̒̀͗̿̈́͆͆̂͆̈́̋̏͊̉͌̒̏̓̑͛̉͘͜͜͜͝͝͠͠s̶");
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);

        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.description()).isNotNull();
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.concreteFuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());

    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(zalgoTextInStringFieldsValidateSanitizeFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}
