package com.endava.cats.fuzzer.fields.only;


import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.IgnoreArguments;
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
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

class OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    @Mock
    private ServiceCaller serviceCaller;
    @Mock
    private TestCaseListener testCaseListener;
    @Mock
    private FilesArguments filesArguments;
    @Mock
    private IgnoreArguments ignoreArguments;

    private OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzer onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer = new OnlyMultiCodePointEmojisInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, ignoreArguments);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(onlyMultiCodePointEmojisInFieldsTrimValidateFuzzer.skipForHttpMethods()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

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