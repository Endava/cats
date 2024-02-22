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
class OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private FilterArguments filterArguments;

    private OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzer onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer = new OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, filesArguments, filterArguments);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.skipForHttpMethods()).isEmpty();

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");

        stringSchema.setMinLength(5);

        fuzzingStrategy = onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "schema").get(1);
        String theEmoji = "\uD83D\uDC68\u200D\uD83C\uDFED️";
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(StringUtils.repeat(theEmoji, (stringSchema.getMinLength() / theEmoji.length()) + 1));
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.getInvisibleChars()).contains("\uD83D\uDC68\u200D\uD83C\uDFED️");
    }

    @Test
    void shouldReturnFirstCharacter() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> schemaMap = new HashMap<>();
        StringSchema stringSchema = new StringSchema();
        schemaMap.put("schema", stringSchema);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);

        FuzzingStrategy fuzzingStrategy = onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(data, "another_schema").get(0);

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\uD83D\uDC69\uD83C\uDFFE");
    }
}