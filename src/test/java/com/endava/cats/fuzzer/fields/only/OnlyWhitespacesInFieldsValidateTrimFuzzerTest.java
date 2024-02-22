package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.http.ResponseCodeFamily;
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
class OnlyWhitespacesInFieldsValidateTrimFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private FilterArguments filterArguments;

    private OnlyWhitespacesInFieldsValidateTrimFuzzer onlyWhitespacesInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        onlyWhitespacesInFieldsValidateTrimFuzzer = new OnlyWhitespacesInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, filesArguments, filterArguments);
    }

    @Test
    void givenANewSpacesOnlyInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInFieldsValidateTrimFuzzer() {
        Assertions.assertThat(onlyWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyWhitespacesInFieldsValidateTrimFuzzer.skipForHttpMethods()).isEmpty();

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyWhitespacesInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(data, "schema").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData().toString()).contains(" ");

        stringSchema.setMinLength(5);

        fuzzingStrategy = onlyWhitespacesInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(data, "schema").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat(" ", stringSchema.getMinLength() + 1));
        Assertions.assertThat(onlyWhitespacesInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInFieldsValidateTrimFuzzer.getInvisibleChars()).contains(" ");
    }
}
