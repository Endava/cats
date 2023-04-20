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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@QuarkusTest
class OnlyControlCharsInFieldsTrimValidateFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil();
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private IgnoreArguments ignoreArguments;

    private OnlyControlCharsInFieldsTrimValidateFuzzer onlyControlCharsInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        onlyControlCharsInFieldsTrimValidateFuzzer = new OnlyControlCharsInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, ignoreArguments);
        Mockito.when(ignoreArguments.getSkipFields()).thenReturn(Collections.singletonList("pet"));
    }

    @Test
    void givenANewTabsOnlyInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTabsOnlyInFieldsTrimValidateFuzzer() {
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.skipForHttpMethods()).isEmpty();

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
        Assertions.assertThat(onlyControlCharsInFieldsTrimValidateFuzzer.skipForFields()).containsOnly("pet");
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
