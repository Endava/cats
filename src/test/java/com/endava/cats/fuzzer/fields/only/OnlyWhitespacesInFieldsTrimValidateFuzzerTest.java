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
class OnlyWhitespacesInFieldsTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private OnlyWhitespacesInFieldsTrimValidateFuzzer onlyWhitespacesInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        onlyWhitespacesInFieldsTrimValidateFuzzer = new OnlyWhitespacesInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewSpacesOnlyInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheSpacesOnlyInFieldsTrimValidateFuzzer() {
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.skipFor()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyWhitespacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");

        stringSchema.setMinLength(5);

        fuzzingStrategy = onlyWhitespacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat(" ", stringSchema.getMinLength() + 1));
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInFieldsTrimValidateFuzzer.getInvisibleChars()).contains(" ");
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
