package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
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
class OnlyControlCharsInFieldsValidateTrimFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil();
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private IgnoreArguments ignoreArguments;

    private OnlyControlCharsInFieldsValidateTrimFuzzer onlyControlCharsInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        onlyControlCharsInFieldsValidateTrimFuzzer = new OnlyControlCharsInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, ignoreArguments);
    }

    @Test
    void shouldOverrideDefaultMethods() {
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.skipForHttpMethods()).isEmpty();
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.getInvisibleChars()).contains("\t");
    }

    @Test
    void shouldReturnProperLengthWhenNoMinLLength() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyControlCharsInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\u0000");
    }

    @Test
    void shouldReturnProperLengthWhenMinValue() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        stringSchema.setMinLength(5);

        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyControlCharsInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat("\u0000", stringSchema.getMinLength() + 1));

    }
}

