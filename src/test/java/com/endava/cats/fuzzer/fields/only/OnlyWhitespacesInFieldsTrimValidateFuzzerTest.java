package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
class OnlyWhitespacesInFieldsTrimValidateFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private FilterArguments filterArguments;
    private OnlyWhitespacesInFieldsTrimValidateFuzzer onlyWhitespacesInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        onlyWhitespacesInFieldsTrimValidateFuzzer = new OnlyWhitespacesInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, filesArguments, filterArguments);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.getInvisibleChars()).contains(" ");
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.skipForHttpMethods()).isEmpty();
    }

    @Test
    void shouldReturnProperLengthWhenNoMinLLength() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyWhitespacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
    }

    @Test
    void shouldReturnProperLengthWhenMinValue() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        stringSchema.setMinLength(5);

        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyWhitespacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat(" ", stringSchema.getMinLength() + 1));

    }

    @Test
    void givenANewSpacesOnlyInFieldsTrimValidateFuzzer_whenGettingTheFieldsFuzzingStrategyAndTheSchemaDoesNotExist_thenTheDefaultNumberOfSpacesAreReturned() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyWhitespacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "another_schema").get(0);

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
    }
}
