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
    void shouldLoadFuzzConfig() throws IOException {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "fuzzersConfig", new File("src/test/resources/fuzzConfig.properties"));
        filesArguments.loadFuzzConfigProperties();
        String existingProperty = filesArguments.getFuzzConfigProperties().getProperty("DummyAcceptHeaders.expectedResponseCode");
        String nonExistingProperty = filesArguments.getFuzzConfigProperties().getProperty("DummyAcceptHeaders.expectedScenario");

        org.assertj.core.api.Assertions.assertThat(existingProperty).isEqualTo("403");
        org.assertj.core.api.Assertions.assertThat(nonExistingProperty).isNull();
    }

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

    @Test
    void shouldLoadRefData() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setRefDataFile(new File("src/test/resources/refFields_with_all.yml"));
        filesArguments.setRefDataArguments(Map.of("argRefData", "argValue", "anotherArgRefData", "anotherValue"));
        filesArguments.loadRefData();
        Map<String, Object> refDataForPets = filesArguments.getRefData("/pets");
        org.assertj.core.api.Assertions.assertThat(refDataForPets).containsOnlyKeys("argRefData", "anotherArgRefData", "allField", "anotherAllField", "field", "name");
    }

    @Test
    void shouldLoadQueryParams() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setQueryFile(new File("src/test/resources/refFields_with_all.yml"));
        filesArguments.setQueryParamsArguments(Map.of("argRefData", "argValue", "anotherArgRefData", "anotherValue"));
        filesArguments.loadQueryParams();
        Map<String, Object> queryParamsForPets = filesArguments.getAdditionalQueryParamsForPath("/pets");
        org.assertj.core.api.Assertions.assertThat(queryParamsForPets).containsOnlyKeys("argRefData", "anotherArgRefData", "allField", "anotherAllField", "field", "name");
    }

    @Test
    void shouldLoadUrlParams() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setParams(List.of("param1:value1", "param2:value2"));
        filesArguments.setUrlParamsArguments(Map.of("param3", "value3", "param4", "value4"));
        filesArguments.loadURLParams();
        List<String> urlParams = filesArguments.getUrlParamsList();
        org.assertj.core.api.Assertions.assertThat(urlParams).containsOnly("param1:value1", "param2:value2", "param3:value3", "param4:value4");
    }

    @Test
    void shouldLoadUrlParamsFromArgsOnly() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setUrlParamsArguments(Map.of("param3", "value3", "param4", "value4"));
        filesArguments.loadURLParams();
        List<String> urlParams = filesArguments.getUrlParamsList();
        org.assertj.core.api.Assertions.assertThat(urlParams).containsOnly("param3:value3", "param4:value4");
    }

    @Test
    void shouldReturnEmptyUrlParamValueWhenNotSupplied() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.loadURLParams();
        String urlParamValue = filesArguments.getUrlParam("myParam");
        org.assertj.core.api.Assertions.assertThat(urlParamValue).isEmpty();
    }

    @Test
    void shouldReturnUrlParamValue() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setUrlParamsArguments(Map.of("param3", "value3", "param4", "value4"));
        filesArguments.loadURLParams();
        String urlParamValue = filesArguments.getUrlParam("param3");
        org.assertj.core.api.Assertions.assertThat(urlParamValue).isEqualTo("value3");
    }
}
