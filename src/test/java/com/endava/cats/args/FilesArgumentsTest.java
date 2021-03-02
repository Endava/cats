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
