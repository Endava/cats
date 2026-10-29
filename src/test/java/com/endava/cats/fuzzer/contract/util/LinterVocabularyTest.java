package com.endava.cats.fuzzer.contract.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

@QuarkusTest
class LinterVocabularyTest {

    @Test
    void shouldIdentifyBodylessStatusCodes() {
        Assertions.assertThat(LinterVocabulary.isBodylessStatus("101")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBodylessStatus("204")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBodylessStatus("304")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBodylessStatus("200")).isFalse();
        Assertions.assertThat(LinterVocabulary.isBodylessStatus(null)).isFalse();
    }

    @Test
    void shouldDetectCaseInsensitiveLocationHeader() {
        Assertions.assertThat(LinterVocabulary.hasLocationHeader(Set.of("location"))).isTrue();
        Assertions.assertThat(LinterVocabulary.hasLocationHeader(List.of("X-Request-Id", "Location"))).isTrue();
        Assertions.assertThat(LinterVocabulary.hasLocationHeader(Set.of("X-Other"))).isFalse();
        Assertions.assertThat(LinterVocabulary.hasLocationHeader(null)).isFalse();
    }

    @Test
    void shouldDetectBinaryAndStreamContentTypes() {
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("image/png")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("audio/mpeg")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("video/mp4")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("application/octet-stream")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("application/pdf")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("text/event-stream")).isTrue();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType("application/json")).isFalse();
        Assertions.assertThat(LinterVocabulary.isBinaryOrStreamContentType(null)).isFalse();
    }

    @Test
    void shouldNormalizePaginationParamNames() {
        Assertions.assertThat(LinterVocabulary.normalizeParamName("page_size")).isEqualTo("pagesize");
        Assertions.assertThat(LinterVocabulary.normalizeParamName("page-number")).isEqualTo("pagenumber");
        Assertions.assertThat(LinterVocabulary.normalizeParamName(null)).isEmpty();
        Assertions.assertThat(LinterVocabulary.PAGINATION_PARAMS).contains("pagesize", "pagenumber", "cursor");
        Assertions.assertThat(LinterVocabulary.SINGLETON_SEGMENTS).contains("me", "health", "status");
    }
}
