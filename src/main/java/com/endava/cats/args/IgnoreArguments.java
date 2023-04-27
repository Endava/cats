package com.endava.cats.args;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Arguments used to ignore CATS response matching. The difference between Filter and Ignore arguments
 * is that Filter arguments are focused on input filtering while Ignore are focused on response filtering.
 */
@Singleton
@Getter
public class IgnoreArguments {

    @CommandLine.Option(names = {"--ignoreResponseCodeUndocumentedCheck", "--iu"},
            description = "Don't check if the response code received from the service matches the value expected by the fuzzer. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseCodeUndocumentedCheck;

    @CommandLine.Option(names = {"--ignoreResponseBodyCheck", "--ib"},
            description = "Don't check if the response body received from the service matches the schema supplied inside the contract. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseBodyCheck;

    @CommandLine.Option(names = {"-i", "--ignoreResponseCodes", "--ic"},
            description = "A comma separated list of HTTP response codes that will be considered as @|bold,underline success|@, even if the Fuzzer will typically report it as @|bold,underline warn|@ or @|bold,underline error|@. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<String> ignoreResponseCodes;

    @CommandLine.Option(names = {"--ignoreResponseSize", "--is"},
            description = "A comma separated list of response sizes that will be considered as @|bold,underline success|@, even if the Fuzzer will typically report it as @|bold,underline warn|@ or @|bold,underline error|@. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<Long> ignoreResponseSizes;

    @CommandLine.Option(names = {"--ignoreResponseWords", "--iw"},
            description = "A comma separated list of word counts in the response that will be considered as @|bold,underline success|@, even if the Fuzzer will typically report it as @|bold,underline warn|@ or @|bold,underline error|@. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<Long> ignoreResponseWords;

    @CommandLine.Option(names = {"--ignoreResponseLines", "--il"},
            description = "A comma separated list of number of line counts in the response that will be considered as @|bold,underline success|@, even if the Fuzzer will typically report it as @|bold,underline warn|@ or @|bold,underline error|@. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<Long> ignoreResponseLines;

    @CommandLine.Option(names = {"--ignoreResponseRegex", "--ir"},
            description = "A regex that will match against the response that will be considered as @|bold,underline success|@, even if the Fuzzer will typically report it as @|bold,underline warn|@ or @|bold,underline error|@. If provided, all Contract Fuzzers will be skipped")
    private String ignoreResponseRegex;

    @CommandLine.Option(names = {"-k", "--skipReportingForIgnoredCodes", "--skipReportingForIgnored"},
            description = "Skip reporting entirely for the ignored response codes, sizes, words and lines provided in @|bold,underline --ignoreResponseXXX|@ arguments. Default: @|bold false|@ ")
    private boolean skipReportingForIgnoredCodes;

    @CommandLine.Option(names = {"--skipFields"},
            description = "A comma separated list of fields that will be skipped by replacement Fuzzers like @|bold EmptyStringsInFields|@, @|bold NullValuesInFields|@, etc.", split = ",")
    private List<String> skipFields;

    @CommandLine.Option(names = {"--skipHeaders"},
            description = "A comma separated list of headers that will be skipped by all Fuzzers", split = ",")
    private List<String> skipHeaders;

    @CommandLine.Option(names = {"-b", "--blackbox"},
            description = "Ignore all response codes except for @|bold,underline 5XX|@ which will be returned as @|bold,underline error|@. This is similar to @|bold --ignoreResponseCodes=\"2xx,4xx\"|@")

    private boolean blackbox;

    public List<String> getIgnoreResponseCodes() {
        List<String> ignored = Optional.ofNullable(this.ignoreResponseCodes).orElse(Collections.emptyList());
        List<String> fromBlackbox = blackbox ? List.of("2xx", "4xx") : Collections.emptyList();

        return Stream.concat(ignored.stream(), fromBlackbox.stream()).toList();
    }

    public boolean isIgnoredResponseCode(String receivedResponseCode) {
        return StringUtils.isNotBlank(receivedResponseCode) &&
                getIgnoreResponseCodes().stream().anyMatch(code -> ResponseCodeFamily.matchAsCodeOrRange(code, receivedResponseCode));
    }

    public boolean isNotIgnoredResponseLength(long length) {
        return !Optional.ofNullable(ignoreResponseSizes).orElse(Collections.emptyList()).contains(length);
    }

    public boolean isNotIgnoredResponseWords(long words) {
        return !Optional.ofNullable(ignoreResponseWords).orElse(Collections.emptyList()).contains(words);
    }

    public boolean isNotIgnoredResponseLines(long lines) {
        return !Optional.ofNullable(ignoreResponseLines).orElse(Collections.emptyList()).contains(lines);
    }

    public boolean isNotIgnoredRegex(String body) {
        return !body.matches(Optional.ofNullable(ignoreResponseRegex).orElse("cats_body"));
    }

    public boolean isNotIgnoredResponse(CatsResponse catsResponse) {
        return !this.isIgnoredResponseCode(catsResponse.responseCodeAsString()) &&
                this.isNotIgnoredResponseLength(catsResponse.getContentLengthInBytes()) &&
                this.isNotIgnoredResponseLines(catsResponse.getNumberOfLinesInResponse()) &&
                this.isNotIgnoredResponseWords(catsResponse.getNumberOfWordsInResponse()) &&
                this.isNotIgnoredRegex(catsResponse.getBody());
    }

    public boolean isIgnoredResponse(CatsResponse catsResponse) {
        return !this.isNotIgnoredResponse(catsResponse);
    }

    public List<String> getSkipFields() {
        return Optional.ofNullable(this.skipFields).orElse(Collections.emptyList());
    }

    public List<String> getSkipHeaders() {
        return Optional.ofNullable(this.skipHeaders).orElse(Collections.emptyList());
    }
}
