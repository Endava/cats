package com.endava.cats.args;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import picocli.CommandLine;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Getter
public class IgnoreArguments {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(IgnoreArguments.class);

    @CommandLine.Option(names = {"--ignoreResponseCodeUndocumentedCheck"},
            description = "Don't check if the response code received from the service matches the value expected by the fuzzer. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseCodeUndocumentedCheck;

    @CommandLine.Option(names = {"--ignoreResponseBodyCheck"},
            description = "Don't check if the response body received from the service matches the schema supplied inside the contract. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseBodyCheck;

    @CommandLine.Option(names = {"-i", "--ignoreResponseCodes"},
            description = "A comma separated list of HTTP response codes that will be considered as @|bold,underline success|@, even if the Fuzzer will typically report it as @|bold,underline warn|@ or @|bold,underline error|@. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<String> ignoreResponseCodes;

    @CommandLine.Option(names = {"-k", "--skipReportingForIgnoredCodes"},
            description = "Skip reporting entirely for the ignored response codes provided in @|bold,underline --ignoreResponseCodes|@. Default: @|bold false|@ ")
    private boolean skipReportingForIgnoredCodes;

    @CommandLine.Option(names = {"--skipFields"},
            description = "A comma separated list of of fields that will be skipped by replacement Fuzzers like @|bold EmptyStringsInFields|@, @|bold NullValuesInFields|@, etc.", split = ",")
    private List<String> skipFields;

    @CommandLine.Option(names = {"-b", "--blackbox"},
            description = "Ignore all response codes except for @|bold,underline 5XX|@ which will be returned as @|bold,underline error|@. This is similar to @|bold --ignoreResponseCodes=\"2xx,4xx\"|@")

    private boolean blackbox;

    public List<String> getIgnoreResponseCodes() {
        List<String> ignored = Optional.ofNullable(this.ignoreResponseCodes).orElse(Collections.emptyList());
        List<String> fromBlackbox = blackbox ? List.of("2xx", "4xx") : Collections.emptyList();

        return Stream.concat(ignored.stream(), fromBlackbox.stream()).collect(Collectors.toList());
    }

    public boolean isIgnoredResponseCode(String receivedResponseCode) {
        return getIgnoreResponseCodes().stream()
                .anyMatch(code -> code.equalsIgnoreCase(receivedResponseCode)
                        || (code.substring(1, 3).equalsIgnoreCase("xx") && code.substring(0, 1).equalsIgnoreCase(receivedResponseCode.substring(0, 1))));

    }

    public List<String> getSkippedFields() {
        return Optional.ofNullable(this.skipFields).orElse(Collections.emptyList());
    }
}
