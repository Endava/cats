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
class OnlyControlCharsInFieldsTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private OnlyControlCharsInFieldsTrimValidateFuzzer onlyControlCharsInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        onlyControlCharsInFieldsTrimValidateFuzzer = new OnlyControlCharsInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewTabsOnlyInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTabsOnlyInFieldsTrimValidateFuzzer() {
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.skipFor()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyControlCharsInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\u0000");

        stringSchema.setMinLength(5);

        fuzzingStrategy = onlyControlCharsInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);

        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat("\u0000", stringSchema.getMinLength() + 1));
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.getInvisibleChars()).contains("\t");
    }

    @Test
    void givenANewTabsOnlyInFieldsTrimValidateFuzzer_whenGettingTheFieldsFuzzingStrategyAndTheSchemaDoesNotExist_thenTheDefaultNumberOfTabsAreReturned() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyControlCharsInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "another_schema").get(0);

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\r\n");
    }
}
