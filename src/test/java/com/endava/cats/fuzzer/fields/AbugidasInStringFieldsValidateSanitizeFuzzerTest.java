package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
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
class AbugidasInStringFieldsValidateSanitizeFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil();
    private AbugidasInStringFieldsValidateSanitizeFuzzer abugidasInStringFieldsValidateSanitizeFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        abugidasInStringFieldsValidateSanitizeFuzzer = new AbugidasInStringFieldsValidateSanitizeFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldProperlyOverrideSuperClassMethods() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("field", new StringSchema());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        FuzzingStrategy fuzzingStrategy = abugidasInStringFieldsValidateSanitizeFuzzer.getFieldFuzzingStrategy(data, "field").get(0);

        Assertions.assertThat(fuzzingStrategy.getData().toString()).contains("్ఞ\u200Cా");
        Assertions.assertThat(abugidasInStringFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(abugidasInStringFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(abugidasInStringFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(abugidasInStringFieldsValidateSanitizeFuzzer.description()).isNotNull();
        Assertions.assertThat(abugidasInStringFieldsValidateSanitizeFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
