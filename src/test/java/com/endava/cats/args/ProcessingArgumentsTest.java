package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import java.util.Map;

@QuarkusTest
class ProcessingArgumentsTest {

    @Test
    void shouldEnableRuntimeResourceReuseByDefault() {
        ProcessingArguments processingArguments = new ProcessingArguments();

        new CommandLine(processingArguments).parseArgs();

        Assertions.assertThat(processingArguments.isReuseSuccessfulResources()).isTrue();
    }

    @Test
    void shouldDisableRuntimeResourceReuseFromCommandLine() {
        ProcessingArguments processingArguments = new ProcessingArguments();

        new CommandLine(processingArguments).parseArgs("--no-reuseSuccessfulResources");

        Assertions.assertThat(processingArguments.isReuseSuccessfulResources()).isFalse();
    }

    @Test
    void shouldDefaultPreseedGetLimitToTwentyWithoutEnablingPreseed() {
        ProcessingArguments processingArguments = new ProcessingArguments();

        new CommandLine(processingArguments).parseArgs();

        Assertions.assertThat(processingArguments.isPreseedCollectionGets()).isFalse();
        Assertions.assertThat(processingArguments.getMaxPreseedCollectionGets()).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "50"})
    void shouldConfigurePreseedGetLimit(int limit) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        CommandLine commandLine = new CommandLine(processingArguments);

        commandLine.parseArgs("--preseedCollectionGets", "--maxPreseedCollectionGets=" + limit);
        processingArguments.validatePreseedCollectionGets(commandLine.getCommandSpec());

        Assertions.assertThat(processingArguments.isPreseedCollectionGets()).isTrue();
        Assertions.assertThat(processingArguments.getMaxPreseedCollectionGets()).isEqualTo(limit);
    }

    @Test
    void shouldRejectNegativePreseedGetLimit() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        CommandLine commandLine = new CommandLine(processingArguments);
        commandLine.parseArgs("--maxPreseedCollectionGets=-1");

        Assertions.assertThatThrownBy(() -> processingArguments.validatePreseedCollectionGets(commandLine.getCommandSpec()))
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("--maxPreseedCollectionGets must be 0 or greater");
    }

    @Test
    void shouldMatchXxxSelectionWhenArgumentNotProvided() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        Assertions.assertThat(processingArguments.matchesXxxSelection("")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"address.line2,Local,true", "address.type,Local,true", "address.type,Global,false"})
    void shouldMatchXxxSelectionWhenArgumentProvidedButNotPresentInPayload(String field, String value, boolean expected) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        String payload = """
                {
                  "address": {
                    "type": "Local",
                    "line1": "my street"
                  }
                }
                """;
        processingArguments.xxxOfSelections = Map.of(field, value);
        Assertions.assertThat(processingArguments.matchesXxxSelection(payload)).isEqualTo(expected);
    }

    @Test
    void shouldReturnDefaultContentTypes() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        Assertions.assertThat(processingArguments.getContentType()).containsExactly(
                "application\\/(?:json|[^;]+\\+json)(?:;.*)?",
                "application/x-www-form-urlencoded", "text/plain", "application/x-ndjson", "application/ndjson");
    }

    @Test
    void shouldReturnProvidedContentType() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "contentType", "app/xml");
        Assertions.assertThat(processingArguments.getContentType()).doesNotContain("application/json", "application/x-www-form-urlencoded").containsExactly("app/xml");
    }

    @Test
    void shouldReturnDefaultContentType() {
        ProcessingArguments processingArguments = new ProcessingArguments();
        Assertions.assertThat(processingArguments.getDefaultContentType()).isEqualTo("application/json");
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,false", "false,true,true", "false,false,false"})
    void shouldTestUsePropertyExamples(boolean usePropertyExamples, boolean useExamples, boolean expected) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "usePropertyExamples", usePropertyExamples);
        ReflectionTestUtils.setField(processingArguments, "useExamples", useExamples);

        Assertions.assertThat(processingArguments.isUsePropertyExamples()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,false", "false,true,true", "false,false,false"})
    void shouldTestUseSchemaExamples(boolean usePropertyExamples, boolean useExamples, boolean expected) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "useSchemaExamples", usePropertyExamples);
        ReflectionTestUtils.setField(processingArguments, "useExamples", useExamples);

        Assertions.assertThat(processingArguments.isUseSchemaExamples()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,false", "false,true,true", "false,false,false"})
    void shouldTestUseResponseBodyExamples(boolean usePropertyExamples, boolean useExamples, boolean expected) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "useResponseBodyExamples", usePropertyExamples);
        ReflectionTestUtils.setField(processingArguments, "useExamples", useExamples);

        Assertions.assertThat(processingArguments.isUseResponseBodyExamples()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,false,false", "false,true,true", "false,false,false"})
    void shouldTestUseRequestBodyExamples(boolean usePropertyExamples, boolean useExamples, boolean expected) {
        ProcessingArguments processingArguments = new ProcessingArguments();
        ReflectionTestUtils.setField(processingArguments, "useRequestBodyExamples", usePropertyExamples);
        ReflectionTestUtils.setField(processingArguments, "useExamples", useExamples);

        Assertions.assertThat(processingArguments.isUseRequestBodyExamples()).isEqualTo(expected);
    }
}
