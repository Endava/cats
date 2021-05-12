package com.endava.cats.args;

import com.endava.cats.CatsMain;
import com.endava.cats.model.CatsSkipped;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig({FilterArguments.class, CheckArguments.class, CatsMain.class})
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
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
        ReflectionTestUtils.setField(checkArguments, argument, "true");
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains(matching).doesNotContain(notMatching);
    }

    @Test
    void shouldReturnAllFuzzersWhenNoCheckSupplied() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzersForPaths", Collections.emptyList());

        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("TopLevelElementsContractInfoFuzzer", "CheckSecurityHeadersFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer");
    }

    @Test
    void shouldRemoveSkippedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "skipFuzzers", "VeryLarge, SecurityHeaders, Jumbo");
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).contains("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer")
                .doesNotContain("CheckSecurityHeadersFuzzer", "VeryLargeStringsFuzzer", "Jumbo");

    }

    @Test
    void shouldOnlyIncludeSuppliedFuzzers() {
        ReflectionTestUtils.setField(filterArguments, "suppliedFuzzers", "VeryLarge, SecurityHeaders, Jumbo");
        List<String> fuzzers = filterArguments.getFuzzersForPath("myPath");

        Assertions.assertThat(fuzzers).doesNotContain("TopLevelElementsContractInfoFuzzer", "HappyFuzzer", "RemoveFieldsFuzzer", "Jumbo")
                .containsOnly("CheckSecurityHeadersFuzzer", "VeryLargeStringsFuzzer");
    }

    @Test
    void givenAContractAndAServerAndASkipFuzzerArgument_whenStartingCats_thenTheSkipForIsCorrectlyProcessed() {
        filterArguments.loadConfig("--contract=src/test/resources/petstore.yml", "--server=http://localhost:8080", "--skipVeryLargeStringsFuzzerForPath=/pets");
        Assertions.assertThat(filterArguments.skipFuzzersForPaths)
                .containsOnly(CatsSkipped.builder().fuzzer("VeryLargeStringsFuzzer").forPaths(Collections.singletonList("/pets")).build());
    }

}
