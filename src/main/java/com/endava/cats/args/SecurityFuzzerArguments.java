package com.endava.cats.args;

import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

/**
 * Holds arguments related to security injection fuzzers.
 */
@Singleton
@Getter
@Setter
public class SecurityFuzzerArguments {

    @CommandLine.Option(names = {"--includeAllInjectionPayloads"},
            description = "Include all injection payloads for security fuzzers (SQL, XSS, Command, NoSQL injection). " +
                    "By default, only a curated top 10 payloads are used per injection type to reduce execution time. " +
                    "Use this flag to enable the full payload set for comprehensive testing.")
    private boolean includeAllInjectionPayloads;
}
