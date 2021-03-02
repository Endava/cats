package com.endava.cats.args;

import com.endava.cats.CatsMain;
import com.endava.cats.model.CatsSkipped;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig({FilterArguments.class, CheckArguments.class, CatsMain.class})
class FilterArgumentsTest {

    @Autowired
    private FilterArguments filterArguments;

    @Autowired
    private CheckArguments checkArguments;

    @ParameterizedTest
    @CsvSource({"checkHeaders,CheckSecurityHeadersFuzzer,RemoveFieldsFuzzer",
            "checkFields,RemoveFieldsFuzzer,CheckSecurityHeadersFuzzer",
            "checkHttp,HappyFuzzer,CheckSecurityHeadersFuzzer",
            "checkContract,TopLevelElementsContractInfoFuzzer,CheckSecurityHeadersFuzzer"})
    void shouldReturnCheckHeadersFuzzers(String argument, String matching, String notMatching) {
        clearCheckArgsFields();

        ReflectionTestUtils.setField(checkArguments, argument, "true");
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }

    @Test
    void shouldReturnAllFuzzersWhenNoCheckSupplied() {
        clearCheckArgsFields();

        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("TopLevelElementsContractInfoFuzzer", "CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer");
    }

    @Test
    void givenAContractAndAServerAndASkipFuzzerArgument_whenStartingCats_thenTheSkipForIsCorrectlyProcessed() {
        filterArguments.loadConfig("--contract=src/test/resources/petstore.yml", "--server=http://localhost:8080", "--skipVeryLargeStringsFuzzerForPath=/pets");
        Assertions.assertThat(filterArguments.skipFuzzersForPaths)
                .containsOnly(CatsSkipped.builder().fuzzer("VeryLargeStringsFuzzer").forPaths(Collections.singletonList("/pets")).build());
    }


    private void clearCheckArgsFields() {
        ReflectionTestUtils.setField(checkArguments, "checkFields", "empty");
        ReflectionTestUtils.setField(checkArguments, "checkHeaders", "empty");
        ReflectionTestUtils.setField(checkArguments, "checkContract", "empty");
        ReflectionTestUtils.setField(checkArguments, "checkHttp", "empty");
    }

}
