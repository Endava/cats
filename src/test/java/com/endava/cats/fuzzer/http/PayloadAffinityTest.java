package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PayloadAffinityTest {
    @Test
    void shouldRejectFieldFuzzersForTextAndNdjsonPayloads() {
        Fuzzer fuzzer = new StructuredFuzzer();

        assertThat(fuzzer.isApplicableTo(data("text/plain"))).isFalse();
        assertThat(fuzzer.isApplicableTo(data("application/x-ndjson"))).isFalse();
        assertThat(fuzzer.isApplicableTo(data("application/json"))).isTrue();
        assertThat(fuzzer.isApplicableTo(data("application/x-www-form-urlencoded"))).isTrue();
    }

    @Test
    void shouldAllowGenericFuzzersForAllPayloadFormats() {
        Fuzzer fuzzer = new GenericFuzzer();

        assertThat(fuzzer.isApplicableTo(data("text/plain"))).isTrue();
        assertThat(fuzzer.isApplicableTo(data("application/x-ndjson"))).isTrue();
    }

    private FuzzingData data(String contentType) {
        return FuzzingData.builder().selectedRequestContentType(contentType).build();
    }

    @FieldFuzzer
    private static final class StructuredFuzzer implements Fuzzer {
        @Override
        public void fuzz(FuzzingData data) {
        }

        @Override
        public String description() {
            return "structured";
        }
    }

    private static final class GenericFuzzer implements Fuzzer {
        @Override
        public void fuzz(FuzzingData data) {
        }

        @Override
        public String description() {
            return "generic";
        }
    }
}
