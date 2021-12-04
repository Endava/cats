package com.endava.cats.args;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import picocli.CommandLine;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Getter
public class IgnoreArguments {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(IgnoreArguments.class);

    @CommandLine.Option(names = {"--ignoreResponseCodeUndocumentedCheck"},
            description = "Don't check if the response code received from the service matches the value expected by the fuzzer. This will return the test result as SUCCESS instead of WARN")
    private boolean ignoreResponseCodeUndocumentedCheck;

    @CommandLine.Option(names = {"--ignoreResponseBodyCheck"},
            description = "Don't check if the response body received from the service matches the schema supplied inside the contract. This will return the test result as SUCCESS instead of WARN")
    private boolean ignoreResponseBodyCheck;

    @CommandLine.Option(names = {"--ignoreResponseCodes"},
            description = "A comma separated list of HTTP response codes that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<String> ignoreResponseCodes;

    @CommandLine.Option(names = {"--skipFields"},
            description = "A comma separated list of of fields that will be skipped by replacement Fuzzers like EmptyStringsInFields, NullValuesInFields, etc.", split = ",")
    private List<String> skipFields;

    @CommandLine.Option(names = {"--blackbox"},
            description = "ignore all response codes expect for 5XX which will be returned as ERROR. This is similar to `--ignoreResponseCodes=\"2xx,4xx\"`")
    private boolean blackbox;

    public List<String> getIgnoreResponseCodes() {
        return Optional.ofNullable(this.ignoreResponseCodes).orElse(Collections.emptyList());
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
