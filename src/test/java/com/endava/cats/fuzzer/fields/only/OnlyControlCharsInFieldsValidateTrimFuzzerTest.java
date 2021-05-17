package com.endava.cats.fuzzer.fields.only;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class OnlyControlCharsInFieldsValidateTrimFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private OnlyControlCharsInFieldsValidateTrimFuzzer onlyControlCharsInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        onlyControlCharsInFieldsValidateTrimFuzzer = new OnlyControlCharsInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewSpacesOnlyInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTabsOnlyInFieldsValidateTrimFuzzer() {
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.skipFor()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyControlCharsInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\u0000");

        stringSchema.setMinLength(5);

        fuzzingStrategy = onlyControlCharsInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat("\u0000", stringSchema.getMinLength() + 1));
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.getInvisibleChars()).contains("\t");
        Assertions.assertThat(onlyControlCharsInFieldsValidateTrimFuzzer.getInvisibleCharDescription()).isEqualTo("unicode control characters");
    }
}

