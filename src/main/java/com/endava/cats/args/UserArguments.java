package com.endava.cats.args;

import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import javax.inject.Singleton;
import java.io.File;

@Singleton
@Getter
public class UserArguments {
    @CommandLine.Option(names = {"--words", "-w"},
            description = "Specifies the user dictionary used by the @|bold TemplateFuzzer|@ to fuzz the specified fields and/or headers")
    @Getter
    @Setter
    private File words;

}
