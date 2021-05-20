package com.endava.cats.args;

import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

class FilesArgumentsTest {

    private final CatsUtil catsUtil = new CatsUtil(Mockito.mock(CatsDSLParser.class));

    @Test
    void shouldReturnSameUrlWhenUrlParamsEmpty() throws Exception {
        FilesArguments filesArguments = new FilesArguments(catsUtil);
        ReflectionTestUtils.setField(filesArguments, "params", "empty");
        filesArguments.loadURLParams();

        String url = "http://localhost:8080";
        org.assertj.core.api.Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(url);
    }

    @Test
    void shouldReplaceUrlWhenUrlParamsSupplied() throws Exception {
        FilesArguments filesArguments = new FilesArguments(catsUtil);
        ReflectionTestUtils.setField(filesArguments, "params", "version:v1.0");
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/{version}";
        String expected = "http://localhost:8080/v1.0";

        org.assertj.core.api.Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(expected);
    }

    @Test
    void shouldNotReplaceUrlWhenUrlParamsSuppliedButNotMatching() throws Exception {
        FilesArguments filesArguments = new FilesArguments(catsUtil);
        ReflectionTestUtils.setField(filesArguments, "params", "someOther:v1.0");
        filesArguments.loadURLParams();

        String url = "http://localhost:8080/{version}";

        org.assertj.core.api.Assertions.assertThat(filesArguments.replacePathWithUrlParams(url)).isEqualTo(url);
    }

    @Test
    void shouldThrowExceptionOnMissingRefData() {
        FilesArguments filesArguments = new FilesArguments(catsUtil);
        ReflectionTestUtils.setField(filesArguments, "refDataFile", "mumu");

        Assertions.assertThrows(IOException.class, filesArguments::loadRefData);
    }

    @Test
    void shouldThrowExceptionOnMissingHeadersFile() {
        FilesArguments filesArguments = new FilesArguments(catsUtil);
        ReflectionTestUtils.setField(filesArguments, "headersFile", "mumu");

        Assertions.assertThrows(IOException.class, filesArguments::loadHeaders);
    }

    @Test
    void shouldThrowExceptionOnNullUrlParams() {
        FilesArguments filesArguments = new FilesArguments(catsUtil);

        Assertions.assertThrows(IOException.class, filesArguments::loadURLParams);
    }
}
