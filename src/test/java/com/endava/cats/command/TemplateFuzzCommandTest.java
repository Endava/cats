package com.endava.cats.command;

import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.special.TemplateFuzzer;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import picocli.CommandLine;

import javax.inject.Inject;

@QuarkusTest
class TemplateFuzzCommandTest {

    @Inject
    TemplateFuzzCommand templateFuzzCommand;
    @Inject
    UserArguments userArguments;
    TemplateFuzzer templateFuzzer;

    @BeforeEach
    void setup() {
        templateFuzzer = Mockito.mock(TemplateFuzzer.class);
        ReflectionTestUtils.setField(templateFuzzCommand, "templateFuzzer", templateFuzzer);
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
        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldRunWithFileData() {
        templateFuzzCommand.data = "@src/test/resources/dict.txt";
        templateFuzzCommand.run();
        Mockito.verify(templateFuzzer, Mockito.times(1)).fuzz(Mockito.any());
    }

    @Test
    void shouldNotRunWithInvalidFileData() {
        templateFuzzCommand.data = "@src/test/resources/dict_non_real.txt";
        templateFuzzCommand.run();
        Mockito.verifyNoInteractions(templateFuzzer);
    }
}
