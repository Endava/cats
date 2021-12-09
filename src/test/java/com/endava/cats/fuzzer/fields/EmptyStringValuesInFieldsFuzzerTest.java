package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
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
class EmptyStringValuesInFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private IgnoreArguments ignoreArguments;
    private CatsUtil catsUtil;
    private FilesArguments filesArguments;

    private EmptyStringValuesInFieldsFuzzer emptyStringValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        catsUtil = Mockito.mock(CatsUtil.class);

        emptyStringValuesInFieldsFuzzer = new EmptyStringValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, ignoreArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunForSkippedFields() {
        Mockito.when(ignoreArguments.getSkippedFields()).thenReturn(Collections.singletonList("id"));
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.skipForFields()).containsOnly("id");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFields()).thenReturn(Sets.newHashSet("id"));
        Mockito.when(data.getPayload()).thenReturn("{}");
        emptyStringValuesInFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener).skipTest(Mockito.any(), Mockito.any());
    }

    @Test
    void givenANewEmptyStringFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheEmptyStringFuzzer() {
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.skipForHttpMethods()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingStrategy fuzzingStrategy = emptyStringValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEmpty();
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
