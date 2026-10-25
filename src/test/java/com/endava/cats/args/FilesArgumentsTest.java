package com.endava.cats.args;

import com.endava.cats.exception.CatsException;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class FilesArgumentsTest {

    @Test
    void shouldLoadFuzzConfig() throws IOException {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "fuzzersConfig", new File("src/test/resources/fuzzConfig.properties"));
        filesArguments.loadFuzzConfigProperties();
        String existingProperty = filesArguments.getFuzzConfigProperties().getProperty("DummyAcceptHeaders.expectedResponseCode");
        String nonExistingProperty = filesArguments.getFuzzConfigProperties().getProperty("DummyAcceptHeaders.expectedScenario");

        Assertions.assertThat(existingProperty).isEqualTo("403");
        Assertions.assertThat(nonExistingProperty).isNull();
    }

    @Test
    void shouldLoadErrorLeaksFile() throws IOException {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "errorLeaksKeywords", new File("src/test/resources/error_leaks.txt"));
        filesArguments.loadErrorLeaksKeywords();

        Set<String> errorLeaksKeywords = filesArguments.getErrorLeaksKeywordsList();
        Assertions.assertThat(errorLeaksKeywords).hasSize(2)
                .containsOnly("this is an error leak", "which is very bad");
    }

    @Test
    void shouldReturnSameUrlWhenUrlParamsEmpty() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", Collections.emptyList());
        filesArguments.loadURLParams();

        String url = "http://localhost:8080";
        Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(url);
    }

    @Test
    void shouldReplaceUrlWhenUrlParamsSupplied() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("version:v1.0"));
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/{version}";
        String expected = "http://localhost:8080/v1.0";

        Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(expected);
    }


    @Test
    void shouldUrlEncodeSuppliedUrlParams() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("tenantId:special chars/and&more"));
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/tenants/{tenantId}";
        String expected = "http://localhost:8080/tenants/special%20chars%2Fand%26more";

        Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(expected);
    }

    @Test
    void shouldPreserveColonsWhenEncodingLegacyUrlParams() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setParams(List.of("schemaPath:localhost:3000/swagger"));
        filesArguments.loadURLParams();

        String result = filesArguments.replacePathWithUrlParams("http://localhost:3000/run/{schemaPath}");

        Assertions.assertThat(result)
                .isEqualTo("http://localhost:3000/run/localhost%3A3000%2Fswagger");
    }

    @Test
    void shouldPreserveColonsWhenEncodingMapUrlParams() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setUrlParamsArguments(Map.of("schemaPath", "https://localhost:3000/swagger"));
        filesArguments.loadURLParams();

        String result = filesArguments.replacePathWithUrlParams("http://localhost:3000/run/{schemaPath}");

        Assertions.assertThat(result)
                .isEqualTo("http://localhost:3000/run/https%3A%2F%2Flocalhost%3A3000%2Fswagger");
    }

    @Test
    void shouldMatchUrlParamNamesExactly() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setParams(List.of("identifier:wrong", "id:localhost:3000/swagger"));
        filesArguments.loadURLParams();

        Assertions.assertThat(filesArguments.getUrlParam("id"))
                .isEqualTo("localhost:3000/swagger");
        Assertions.assertThat(filesArguments.isNotUrlParam("ident")).isTrue();
    }

    @Test
    void shouldNotReplaceUrlWhenUrlParamsSuppliedButNotMatching() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "params", List.of("someOther:v1.0"));
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/{version}";

        Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(url);
    }

    @Test
    void shouldThrowExceptionOnMissingRefData() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "refDataFile", new File("mumu"));

        Assertions.assertThatThrownBy(filesArguments::loadRefData).isInstanceOf(IOException.class);
    }

    @Test
    void shouldThrowExceptionOnMissingHeadersFile() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("mumu"));

        Assertions.assertThatThrownBy(filesArguments::loadHeaders).isInstanceOf(IOException.class);
    }

    @Test
    void shouldMergeHeaders() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("src/test/resources/headers.yml"));
        ReflectionTestUtils.setField(filesArguments, "headersMap", Map.of("auth", "secret"));
        filesArguments.loadHeaders();
        Map<String, Object> headers = filesArguments.getHeaders("/some-path");

        Assertions.assertThat(headers).hasSize(3).containsOnlyKeys("auth", "catsFuzzedHeader", "header");
    }

    @Test
    void shouldThrowExceptionOnMissingHeadersQueryParamsFile() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("mumu"));

        Assertions.assertThatThrownBy(filesArguments::loadQueryParams).isInstanceOf(IOException.class);
    }

    @Test
    void shouldThrowExceptionAsInvalidFormatForHeaders() {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "headersFile", new File("src/test/resources/wrong_headers.yml"));
        Assertions.assertThatThrownBy(filesArguments::loadHeaders).isInstanceOf(CatsException.class)
                .hasMessage("File format is wrong for Headers. Make sure you supply a valid yaml file!");
    }

    @Test
    void shouldLoadPathAndAllQueryParamsForPath() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParams.yml"));
        filesArguments.loadQueryParams();

        Assertions.assertThat(filesArguments.getAdditionalQueryParamsForPath("/pets"))
                .containsOnlyKeys("param", "jwt");
    }

    @Test
    void shouldLoadOnlyAllQueryParamsForPath() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/queryParams.yml"));
        filesArguments.loadQueryParams();

        Assertions.assertThat(filesArguments.getAdditionalQueryParamsForPath("/no-pets"))
                .containsOnlyKeys("param");
    }

    @Test
    void shouldNotLoadAnyQueryParam() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        ReflectionTestUtils.setField(filesArguments, "queryFile", new File("src/test/resources/refFields.yml"));
        filesArguments.loadQueryParams();
        Assertions.assertThat(filesArguments.getAdditionalQueryParamsForPath("/no-pets")).isEmpty();
    }

    @Test
    void shouldReturnEmptyUrlParams() {
        FilesArguments filesArguments = new FilesArguments();

        filesArguments.loadURLParams();
        Assertions.assertThat(filesArguments.getUrlParamsList()).isEmpty();
    }

    @Test
    void shouldProperlyParseYamlFile() throws Exception {
        Map<String, Map<String, Object>> yaml = FilesArguments.parseYaml("src/test/resources/test.yml");

        Assertions.assertThat(yaml.get("all")).isNotNull();
        Assertions.assertThat(yaml.get("all").get("Authorization")).isNotNull();
    }

    @Test
    void shouldLoadRefData() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setRefDataFile(new File("src/test/resources/refFields_with_all.yml"));
        filesArguments.setRefDataArguments(Map.of("argRefData", "argValue", "anotherArgRefData", "anotherValue"));
        filesArguments.loadRefData();
        Map<String, Object> refDataForPets = filesArguments.getRefData("/pets");
        Assertions.assertThat(refDataForPets).containsOnlyKeys("argRefData", "anotherArgRefData", "allField", "anotherAllField", "field", "name");
    }

    @Test
    void shouldLoadQueryParams() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setQueryFile(new File("src/test/resources/refFields_with_all.yml"));
        filesArguments.setQueryParamsArguments(Map.of("argRefData", "argValue", "anotherArgRefData", "anotherValue"));
        filesArguments.loadQueryParams();
        Map<String, Object> queryParamsForPets = filesArguments.getAdditionalQueryParamsForPath("/pets");
        Assertions.assertThat(queryParamsForPets).containsOnlyKeys("argRefData", "anotherArgRefData", "allField", "anotherAllField", "field", "name");
    }

    @Test
    void shouldLoadUrlParams() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setParams(List.of("param1:value1", "param2:value2"));
        filesArguments.setUrlParamsArguments(Map.of("param3", "value3", "param4", "value4"));
        filesArguments.loadURLParams();
        List<String> urlParams = filesArguments.getUrlParamsList();
        Assertions.assertThat(urlParams).containsOnly("param1:value1", "param2:value2", "param3:value3", "param4:value4");
    }

    @Test
    void shouldLoadUrlParamsFromArgsOnly() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setUrlParamsArguments(Map.of("param3", "value3", "param4", "value4"));
        filesArguments.loadURLParams();
        List<String> urlParams = filesArguments.getUrlParamsList();
        Assertions.assertThat(urlParams).containsOnly("param3:value3", "param4:value4");
    }

    @Test
    void shouldReturnEmptyUrlParamValueWhenNotSupplied() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.loadURLParams();
        String urlParamValue = filesArguments.getUrlParam("myParam");
        Assertions.assertThat(urlParamValue).isEmpty();
    }

    @Test
    void shouldReturnUrlParamValue() {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setUrlParamsArguments(Map.of("param3", "value3", "param4", "value4"));
        filesArguments.loadURLParams();
        String urlParamValue = filesArguments.getUrlParam("param3");
        Assertions.assertThat(urlParamValue).isEqualTo("value3");
    }

    @Test
    void shouldLoadPathsOrder() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setPathsOrderFile(new File("src/test/resources/paths.order"));
        filesArguments.loadPathsOrder();
        Assertions.assertThat(filesArguments.getPathsOrder()).hasSize(2).containsExactly("/path1", "/path2");
    }

    @Test
    void shouldLoadMutators() throws Exception {
        FilesArguments filesArguments = new FilesArguments();
        filesArguments.setMutatorsFolder(new File("src/test/resources/mutators"));
        filesArguments.loadMutators();

        Assertions.assertThat(filesArguments.getMutatorsFolder()).isDirectory();
    }

    @Test
    void shouldCopyFromOtherInstanceAndFilterRemovedFields() {
        FilesArguments source = new FilesArguments();
        source.setCreateRefData(true);
        source.setParams(List.of("id:1"));
        source.setHeadersMap(Map.of("X-Test", "1"));
        source.setQueryParamsArguments(Map.of("q", "v"));
        source.setRefDataArguments(Map.of("r", "v"));

        FilesArguments target = new FilesArguments();
        target.copyFrom(null);
        target.copyFrom(target);
        target.copyFrom(source);

        Assertions.assertThat(target.isCreateRefData()).isTrue();
        Assertions.assertThat(FilesArguments.getFieldsToRemoveFromRefData(null)).isEmpty();
        Assertions.assertThat(FilesArguments.getFieldsToRemoveFromRefData(Map.of())).isEmpty();
        Assertions.assertThat(FilesArguments.getFieldsToRemoveFromRefData(
                Map.of("keep", "val", "address.city", ServiceCaller.CATS_REMOVE_FIELD, "omit", List.of("nested.drop", "legacy"))))
                .containsExactlyInAnyOrder("address#city", "nested#drop", "legacy");
        Assertions.assertThat(FilesArguments.getFieldsToRemoveFromRefData(
                Map.of("omit", "a.b, c.d")))
                .containsExactlyInAnyOrder("a#b", "c#d");

        Map<String, Map<String, Object>> scopedData = Map.of(
                "all", Map.of("globalKey", "g", "overrideKey", "fromAll", "omit", List.of("globalOmitted")),
                "/orders*", Map.of("wildcardKey", "w", "overrideKey", "fromWildcard"),
                "POST /orders*", Map.of("methodWildcardKey", "mw", "overrideKey", "fromMethodWildcard"),
                "/orders/{id}", Map.of("exactPathKey", "ep", "overrideKey", "fromExactPath"),
                "POST /orders/{id}", Map.of("exactMethodKey", "em", "overrideKey", "fromExactMethod", "omit", List.of("postOmitted"))
        );
        ReflectionTestUtils.setField(target, "refData", scopedData);
        ReflectionTestUtils.setField(target, "headers", scopedData);

        Map<String, Object> postOrders = target.getRefData("/orders/{id}", HttpMethod.POST);
        Assertions.assertThat(postOrders)
                .containsEntry("overrideKey", "fromExactMethod")
                .containsEntry("exactMethodKey", "em")
                .containsEntry("exactPathKey", "ep")
                .containsEntry("methodWildcardKey", "mw")
                .containsEntry("wildcardKey", "w")
                .containsEntry("globalKey", "g")
                .containsEntry("postOmitted", ServiceCaller.CATS_REMOVE_FIELD)
                .containsEntry("globalOmitted", ServiceCaller.CATS_REMOVE_FIELD);

        Map<String, Object> getOrders = target.getHeaders("/orders/{id}", HttpMethod.GET);
        Assertions.assertThat(getOrders)
                .containsEntry("overrideKey", "fromExactPath")
                .doesNotContainKey("exactMethodKey")
                .doesNotContainKey("methodWildcardKey");

        Assertions.assertThat(target.mergePathAndAll(null, "/orders", HttpMethod.GET)).isEmpty();
        Assertions.assertThat(target.mergePathAndAll(Map.of(), "/orders", HttpMethod.GET)).isEmpty();
    }
}
