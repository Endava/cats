package com.endava.cats.args;

import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import jakarta.inject.Singleton;

/**
 * Holds all args related to category of Fuzzers to run.
 */
@Singleton
@Getter
@Setter
public class CheckArguments {
    @CommandLine.Option(names = {"-A", "--checkHeaders"},
            description = "Run only Header Fuzzers")
    private boolean checkHeaders;

    @CommandLine.Option(names = {"-F", "--checkFields"},
            description = "Run only Fields Fuzzers")
    private boolean checkFields;

    @CommandLine.Option(names = {"-T", "--checkHttp"},
            description = "Run only HTTP Fuzzers")
    private boolean checkHttp;

    @CommandLine.Option(names = {"-C", "--checkContract", "--includeContract"},
            description = "Include Contract Fuzzers")
    private boolean includeContract;

    @CommandLine.Option(names = {"-W", "--includeWhitespaces"},
            description = "Include Whitespaces Fuzzers")
    private boolean includeWhitespaces;

    @CommandLine.Option(names = {"-E", "--includeEmojis"},
            description = "Include Emojis Fuzzers")
    private boolean includeEmojis;

    @CommandLine.Option(names = {"-U", "--includeControlChars"},
            description = "Include ControlChars Fuzzers")
    private boolean includeControlChars;

}
