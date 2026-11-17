package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.SecurityFindingType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class SecurityFindingCoverageTest {
    @Inject
    @Any
    Instance<BaseSecurityInjectionFuzzer> fuzzers;

    @Test
    void shouldRecognizeTheConfirmableReasonOfEveryInjectionFuzzer() {
        Assertions.assertThat(fuzzers.stream()).isNotEmpty();
        fuzzers.forEach(SecurityFindingCoverageTest::assertRecognizedReason);
    }

    private static void assertRecognizedReason(BaseSecurityInjectionFuzzer fuzzer) {
        Assertions.assertThat(SecurityFindingType.fromReason(fuzzer.getConfirmableFindingReason()))
                .as("Confirmable reason from %s", fuzzer.getClass().getSimpleName()).isPresent();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SSTI|result: 8051|true",
            "SSTI|template processing failed|false",
            "XXE|root:x:0:0:root:/root:/bin/bash|true",
            "XXE|DOCTYPE is disallowed|false",
            "PATH_TRAVERSAL|root:x:0:0:root:/root:/bin/bash|true",
            "PATH_TRAVERSAL|java.io.FileNotFoundException: /tmp/missing|false"
    })
    void shouldRecognizeTheActualStrongAndWeakDetectorTitles(SecurityFindingType type, String body, boolean strong) {
        BaseSecurityInjectionFuzzer.InjectionDetectionResult detection = detector(type).detectInjectionEvidence(
                CatsResponse.builder().responseCode(200).body(body).build(), FuzzingData.builder().build());

        Assertions.assertThat(detection.isVulnerable()).isTrue();
        Assertions.assertThat(detection.isStrong()).isEqualTo(strong);
        Assertions.assertThat(SecurityFindingType.fromReason(detection.getTitle())).contains(type);
    }

    private BaseSecurityInjectionFuzzer detector(SecurityFindingType type) {
        return fuzzers.stream()
                .filter(fuzzer -> SecurityFindingType.fromReason(fuzzer.getConfirmableFindingReason()).filter(type::equals).isPresent())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No detector fixture for " + type));
    }
}
