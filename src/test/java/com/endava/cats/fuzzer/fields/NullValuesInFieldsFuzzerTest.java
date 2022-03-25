package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.common.collect.Sets;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Set;

@QuarkusTest
class NullValuesInFieldsFuzzerTest {
    private IgnoreArguments ignoreArguments;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private NullValuesInFieldsFuzzer nullValuesInFieldsFuzzer;
    private FilesArguments filesArguments;
    private CatsUtil catsUtil;

    @BeforeEach
    void setup() {
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        catsUtil = Mockito.mock(CatsUtil.class);
        nullValuesInFieldsFuzzer = new NullValuesInFieldsFuzzer(null, testCaseListener, catsUtil, filesArguments, ignoreArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForSkippedFields() {
        Mockito.when(ignoreArguments.getSkipFields()).thenReturn(Collections.singletonList("id"));
        Assertions.assertThat(nullValuesInFieldsFuzzer.skipForFields()).containsOnly("id");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Sets.newHashSet("id"));
        Mockito.when(data.getPayload()).thenReturn("{}");
        nullValuesInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.any());
    }

    @Test
    void givenANewNullValuesInFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheNullValuesInFieldsFuzzer() {
        Assertions.assertThat(nullValuesInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);

        FuzzingStrategy fuzzingStrategy = nullValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isNull();
        Assertions.assertThat(nullValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(nullValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotRunFuzzerWhenGetButNoQueryParam() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).queryParams(Set.of("query1")).build();
        Assertions.assertThat(nullValuesInFieldsFuzzer.isFuzzingPossibleSpecificToFuzzer(data, "notQuery", FuzzingStrategy.replace())).isFalse();
    }

    @Test
    void shouldRunFuzzerWhenGetAndQueryParam() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET).queryParams(Set.of("query1")).build();
        Assertions.assertThat(nullValuesInFieldsFuzzer.isFuzzingPossibleSpecificToFuzzer(data, "query1", FuzzingStrategy.replace())).isTrue();
    }

    @Test
    void shouldRunFuzzerWhenPost() {
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).build();
        Assertions.assertThat(nullValuesInFieldsFuzzer.isFuzzingPossibleSpecificToFuzzer(data, "notQuery", FuzzingStrategy.replace())).isTrue();
    }

}
