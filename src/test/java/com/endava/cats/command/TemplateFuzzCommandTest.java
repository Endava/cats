package com.endava.cats.command;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.special.TemplateFuzzer;
import com.endava.cats.http.HttpMethod;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

@QuarkusTest
class TemplateFuzzCommandTest {

    @Inject
    TemplateFuzzCommand templateFuzzCommand;
    MatchArguments matchArguments;
    @Inject
    UserArguments userArguments;
    TemplateFuzzer templateFuzzer;
    StopArguments stopArguments;
    IgnoreArguments ignoreArguments;

    @BeforeEach
    void setup() {
        templateFuzzer = Mockito.mock(TemplateFuzzer.class);
        ReflectionTestUtils.setField(templateFuzzCommand, "templateFuzzer", templateFuzzer);
        matchArguments = Mockito.mock(MatchArguments.class);
        ReflectionTestUtils.setField(templateFuzzCommand, "matchArguments", matchArguments);
        stopArguments = Mockito.mock(StopArguments.class);
        ReflectionTestUtils.setField(templateFuzzCommand, "stopArguments", stopArguments);
        ignoreArguments = Mockito.mock(IgnoreArguments.class);
        ReflectionTestUtils.setField(templateFuzzCommand, "ignoreArguments", ignoreArguments);
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
    }

    @Test
    void shouldNotRunWhenDataNotSuppliedAndPost() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        Assertions.assertThatThrownBy(() -> templateFuzzCommand.run()).isInstanceOf(CommandLine.ParameterException.class).hasMessage("Missing required option --data=<data>");
    }

    @Test
    void shouldRunWithCommandLineData() {
        templateFuzzCommand.data = "{\"field\":\"value\"}";
        templateFuzzCommand.url = "localhost";
        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldRunWhenGetAndNoData() {
        templateFuzzCommand.data = null;
        templateFuzzCommand.httpMethod = HttpMethod.GET;
        templateFuzzCommand.url = "localhost/FUZZ";
        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldRunWithFileData() {
        templateFuzzCommand.data = "@src/test/resources/data.json";
        templateFuzzCommand.url = "localhost";

        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldNotRunWithInvalidFileData() {
        templateFuzzCommand.data = "@src/test/resources/dict_non_real.txt";
        templateFuzzCommand.run();
        Mockito.verifyNoInteractions(templateFuzzer);
    }

    @Test
    void shouldThrowExceptionWhenNoTargetFieldAndNoFuzzAndMethodGet() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        templateFuzzCommand.data = "@src/test/resources/data.json";
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        templateFuzzCommand.httpMethod = HttpMethod.GET;
        templateFuzzCommand.url = "localhost";

        Assertions.assertThatThrownBy(() -> templateFuzzCommand.run()).isInstanceOf(CommandLine.ParameterException.class).hasMessage("You must provide either --targetFields or the FUZZ keyword");
    }

    @Test
    void shouldRunWhenFuzzKeywordInPath() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        templateFuzzCommand.data = "@src/test/resources/data.json";
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        templateFuzzCommand.httpMethod = HttpMethod.GET;
        templateFuzzCommand.url = "http://localhost/FUZZ";
        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }


    @Test
    void shouldRunWhenFuzzKeywordInPayload() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        templateFuzzCommand.data = """
                {"field": "value", "anotherField": "FUZZ"}
                """;
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        templateFuzzCommand.url = "http://localhost";
        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldThrowExceptionWhenNoMatchOrIgnoreArgumentSupplied() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        templateFuzzCommand.data = "@src/test/resources/data.json";
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(false);
        Mockito.when(ignoreArguments.isAnyIgnoreArgumentSupplied()).thenReturn(false);

        Assertions.assertThatThrownBy(() -> templateFuzzCommand.run()).isInstanceOf(CommandLine.ParameterException.class).hasMessage("At least one --matchXXX, --ignoreXXX or --filterXXX argument is required");
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void shouldNotThrowExceptionWhenArgumentsSupplied(boolean match, boolean ignore) {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        templateFuzzCommand.data = "@src/test/resources/data.json";
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(match);
        Mockito.when(ignoreArguments.isAnyIgnoreArgumentSupplied()).thenReturn(ignore);
        templateFuzzCommand.url = "http://localhost";

        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldThrowExceptionWhenRandomAndNoStopArgumentSupplied() {
        CommandLine.Model.CommandSpec spec = Mockito.mock(CommandLine.Model.CommandSpec.class);
        Mockito.when(spec.commandLine()).thenReturn(Mockito.mock(CommandLine.class));
        ReflectionTestUtils.setField(templateFuzzCommand, "spec", spec);
        templateFuzzCommand.data = "@src/test/resources/data.json";
        Mockito.when(stopArguments.isAnyStopConditionProvided()).thenReturn(false);
        templateFuzzCommand.setRandom(true);

        Assertions.assertThatThrownBy(() -> templateFuzzCommand.run()).isInstanceOf(CommandLine.ParameterException.class).hasMessage("When running in continuous fuzzing mode, at least one --stopXXX argument must be provided");
    }
}
