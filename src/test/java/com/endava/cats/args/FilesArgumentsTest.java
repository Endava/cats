package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@QuarkusTest
class FilesArgumentsTest {

    @Test
    void shouldReturnSameUrlWhenUrlParamsEmpty() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", Collections.emptyList());
        filesArguments.loadURLParams();

        String url = "http://localhost:8080";
        org.assertj.core.api.Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(url);
    }

    @Test
    void shouldReplaceUrlWhenUrlParamsSupplied() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("version:v1.0"));
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/{version}";
        String expected = "http://localhost:8080/v1.0";

        org.assertj.core.api.Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(expected);
    }

    @Test
    void shouldNotReplaceUrlWhenUrlParamsSuppliedButNotMatching() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("someOther:v1.0"));
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/{version}";

        org.assertj.core.api.Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(url);
    }

    @Test
    void shouldThrowExceptionOnMissingRefData() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "refDataFile", new File("mumu"));

        Assertions.assertThrows(IOException.class, filesArguments::loadRefData);
    }

    @Test
    void shouldThrowExceptionOnMissingHeadersFile() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("mumu"));

        Assertions.assertThrows(IOException.class, filesArguments::loadHeaders);
    }

    @Test
    void shouldMergeHeaders() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("src/test/resources/headers.yml"));
        ReflectionTestUtils.setField(filesArguments, "headersMap", Map.of("auth", "secret"));
        filesArguments.loadHeaders();
        Map<String, Object> headers = filesArguments.getHeaders("all");

        org.assertj.core.api.Assertions.assertThat(headers).hasSize(3).containsOnlyKeys("auth", "catsFuzzedHeader", "header");
    }

    @Test
    void shouldThrowExceptionOnMissingHeadersQueryParamsFile() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("mumu"));

        Assertions.assertThrows(IOException.class, filesArguments::loadQueryParams);
    }

    @Test
    void shouldLoadPathAndAllQueryParamsForPath() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParams.yml"));
        filesArguments.loadQueryParams();

        org.assertj.core.api.Assertions.assertThat(filesArguments.getAdditionalQueryParamsForPath("/pets"))
                .containsOnlyKeys("param", "jwt");
    }

    @Test
    void shouldLoadOnlyAllQueryParamsForPath() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParams.yml"));
        filesArguments.loadQueryParams();

        org.assertj.core.api.Assertions.assertThat(filesArguments.getAdditionalQueryParamsForPath("/no-pets"))
                .containsOnlyKeys("param");
    }

    @Test
    void shouldNotLoadAnyQueryParam() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/refFields.yml"));
        filesArguments.loadQueryParams();
        org.assertj.core.api.Assertions.assertThat(filesArguments.getAdditionalQueryParamsForPath("/no-pets")).isEmpty();
    }

    @Test
    void shouldReturnEmptyUrlParams() {
        FilesArguments filesArguments = new FilesArguments();

        filesArguments.loadURLParams();
        org.assertj.core.api.Assertions.assertThat(filesArguments.getUrlParamsList()).isEmpty();
    }

    @Test
    void shouldProperlyParseYamlFile() throws Exception {
        Map<String, Map<String, Object>> yaml = new FilesArguments().parseYaml("src/test/resources/test.yml");

        org.assertj.core.api.Assertions.assertThat(yaml.get("all")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(yaml.get("all").get("Authorization")).isNotNull();
    }
}
