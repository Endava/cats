package com.endava.cats.args;

import lombok.Getter;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;

/**
 * Holds all args related to category of Fuzzers to run.
 */
@ApplicationScoped
@Getter
public class CheckArguments {
    @CommandLine.Option(names = {"--checkHeaders"},
            description = "Run only Header Fuzzers")
    private boolean checkHeaders;

    @CommandLine.Option(names = {"--checkFields"},
            description = "Run only Fields Fuzzers")
    private boolean checkFields;

    @CommandLine.Option(names = {"--checkHttp"},
            description = "Run only HTTP Fuzzers")
    private boolean checkHttp;

    @CommandLine.Option(names = {"--checkContract"},
            description = "Run only Contract Fuzzers")
    private boolean checkContract;

    @CommandLine.Option(names = {"--includeWhitespaces"},
            description = "Include Whitespaces Fuzzers")
    private boolean includeWhitespaces;

    @CommandLine.Option(names = {"--includeEmojis"},
            description = "Include Emojis Fuzzers")
    private boolean includeEmojis;

    @CommandLine.Option(names = {"--includeControlChars"},
            description = "Include ControlChars Fuzzers")
    private boolean includeControlChars;

}
