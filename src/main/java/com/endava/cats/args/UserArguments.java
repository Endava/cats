package com.endava.cats.args;

import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Singleton
@Getter
public class UserArguments {
    @CommandLine.Option(names = {"--words", "-w"},
            description = "Specifies the user dictionary used by the @|bold TemplateFuzzer|@ to fuzz the specified fields and/or headers")
    @Setter
    File words;

    public List<String> getWordsAsList() {
        try {
            return Files.readAllLines(Path.of(words.getAbsolutePath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
