package com.endava.cats.fuzzer.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class JsonNestingInputsTest {
    @Test
    void shouldBoundTheCorpusAndValidateAllDocumentsWithAnIndependentStreamingParser() throws IOException {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2048).build()).build();
        assertThat(JsonNestingInputs.probes()).hasSize(24).extracting(JsonNestingInputs.Probe::payload).doesNotHaveDuplicates();
        for (var probe : JsonNestingInputs.probes()) {
            assertThat(probe.payload().getBytes(StandardCharsets.UTF_8)).hasSizeLessThan(8192);
            try (var parser = factory.createParser(probe.payload())) {
                assertThat(parser.nextToken()).isNotNull();
                if (probe.truncated()) {
                    assertThatThrownBy(parser::skipChildren).isInstanceOf(IOException.class);
                } else {
                    parser.skipChildren();
                    assertThat(parser.nextToken()).isNull();
                }
            }
        }
    }
}
