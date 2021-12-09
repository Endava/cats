package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
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
        Mockito.when(ignoreArguments.getSkippedFields()).thenReturn(Collections.singletonList("id"));
        Assertions.assertThat(nullValuesInFieldsFuzzer.skipForFields()).containsOnly("id");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFields()).thenReturn(Sets.newHashSet("id"));
        Mockito.when(data.getPayload()).thenReturn("{}");
        nullValuesInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.any());
    }

    @Test
    void givenANewNullValuesInFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheNullValuesInFieldsFuzzer() {
        Assertions.assertThat(nullValuesInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(nullValuesInFieldsFuzzer.skipForHttpMethods()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingStrategy fuzzingStrategy = nullValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isNull();
        Assertions.assertThat(nullValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(nullValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
