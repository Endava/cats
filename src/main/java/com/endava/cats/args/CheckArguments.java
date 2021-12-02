package com.endava.cats.args;

import lombok.Getter;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Holds all args related to category of Fuzzers to run.
 */
@Component
@Getter
public class CheckArguments {
    @CommandLine.Option(names = {"--checkHeaders"},
            description = "Instructs CATS to only run Header Fuzzers")
    private boolean checkHeaders;

    @CommandLine.Option(names = {"--checkFields"},
            description = "Instructs CATS to only run Fields Fuzzers")
    private boolean checkFields;

    @CommandLine.Option(names = {"--checkHttp"},
            description = "Instructs CATS to only run HTTP Fuzzers")
    private boolean checkHttp;

    @CommandLine.Option(names = {"--checkContract"},
            description = "Instructs CATS to only run Contract Fuzzers")
    private boolean checkContract;

    @CommandLine.Option(names = {"--includeWhitespaces"},
            description = "Instructs CATS to include Whitespaces Fuzzers")
    private boolean includeWhitespaces;

    @CommandLine.Option(names = {"--includeEmojis"},
            description = "Instructs CATS to include Emojis Fuzzers")
    private boolean includeEmojis;

    @CommandLine.Option(names = {"--includeControlChars"},
            description = "Instructs CATS to include ControlChars Fuzzers")
    private boolean includeControlChars;

}
