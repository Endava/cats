package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.common.collect.Sets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
class EmptyStringValuesInFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    @Mock
    private FilterArguments filterArguments;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    private EmptyStringValuesInFieldsFuzzer emptyStringValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        emptyStringValuesInFieldsFuzzer = new EmptyStringValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments, filterArguments);
    }

    @Test
    void shouldNotRunForSkippedFields() {
        Mockito.when(filterArguments.getSkippedFields()).thenReturn(Collections.singletonList("id"));
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.skipForFields()).containsOnly("id");
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFields()).thenReturn(Sets.newHashSet("id"));
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
