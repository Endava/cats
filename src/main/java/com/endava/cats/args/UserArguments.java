package com.endava.cats.args;

import com.endava.cats.exception.CatsException;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Holds user specific input such as custom dictionaries.
 */
@Singleton
@Getter
@Setter
public class UserArguments {
    @CommandLine.Option(names = {"--words", "-w"},
            description = "Specifies a custom user dictionary that will be used for fields and/or headers values fuzzing")
    File words;

    @CommandLine.Option(names = {"--nameReplace", "--simpleReplace"},
            description = "If set to true, it will simply do a replace between the targetFields names provided and the fuzz values")
    boolean nameReplace;

    /**
     * Gets the custom provided dictionary as a list of strings where each row is an item.
     *
     * @return a list with all the custom dictionary words
     */
    public List<String> getWordsAsList() {
        try {
            return Files.readAllLines(Path.of(words.getAbsolutePath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CatsException("Unable to process the --words file. Either the file does not exist or it's not reachable", e);
        }
    }

    /**
     * Checks if a user dictionary was supplied through the arguments.
     *
     * @return true if a user dictionary is supplied, false otherwise
     */
    public boolean isUserDictionarySupplied() {
        return this.words != null;
    }

}
