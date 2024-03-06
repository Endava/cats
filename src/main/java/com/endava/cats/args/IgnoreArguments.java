package com.endava.cats.args;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

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
            description = "Don't check if the response code received from is documented inside the contract. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseCodeUndocumentedCheck;

    @CommandLine.Option(names = {"--ignoreResponseBodyCheck", "--ib"},
            description = "Don't check if the response body received from the service matches the schema supplied inside the contract. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseBodyCheck;

    @CommandLine.Option(names = {"--ignoreResponseContentTypeCheck", "--it"},
            description = "Don't check if the response content type received from the service matches the one supplied inside the contract. This will return the test result as @|bold,underline success|@ instead of @|bold,underline warn|@")
    private boolean ignoreResponseContentTypeCheck;

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

    @CommandLine.Option(names = {"-k", "--skipReportingForIgnoredCodes", "--skipReportingForIgnored", "--sri"},
            description = "Skip reporting entirely for the ignored response codes, sizes, words and lines provided in @|bold,underline --ignoreResponseXXX|@ arguments. Default: @|bold false|@ ")
    private boolean skipReportingForIgnoredCodes;

    @CommandLine.Option(names = {"--srs", "--skipReportingForSuccess"},
            description = "Skip reporting entirely for tests cases reported as success. Default: @|bold false|@ ")
    private boolean skipReportingForSuccess;

    @CommandLine.Option(names = {"--srw", "--skipReportingForWarning"},
            description = "Skip reporting entirely for tests cases reported as warnings. Default: @|bold false|@ ")
    private boolean skipReportingForWarnings;

    @CommandLine.Option(names = {"-b", "--blackbox"},
            description = "Ignore all response codes except for @|bold,underline 5XX|@ which will be returned as @|bold,underline error|@. This is similar to @|bold --ignoreResponseCodes=\"2xx,4xx\"|@")

    private boolean blackbox;

    /**
     * Returns a list with all response codes.
     *
     * @return a list of response codes to ignore
     */
    public List<String> getIgnoreResponseCodes() {
        List<String> ignored = Optional.ofNullable(this.ignoreResponseCodes).orElse(Collections.emptyList());
        List<String> fromBlackbox = blackbox ? List.of("2xx", "4xx", "501") : Collections.emptyList();

        return Stream.concat(ignored.stream(), fromBlackbox.stream()).toList();
    }

    /**
     * Checks if the supplied response code is ignored.
     *
     * @param receivedResponseCode the response code to check
     * @return true if the response code should be ignored, false otherwise
     */
    public boolean isIgnoredResponseCode(String receivedResponseCode) {
        return StringUtils.isNotBlank(receivedResponseCode) &&
                getIgnoreResponseCodes().stream().anyMatch(code -> ResponseCodeFamily.matchAsCodeOrRange(code, receivedResponseCode));
    }

    /**
     * Checks if the response length received in response are not ignored.
     *
     * @param length the length of the http response
     * @return true if the length of the response does not match the --ignoreResponseSizes argument, false otherwise
     */
    public boolean isNotIgnoredResponseLength(long length) {
        return !Optional.ofNullable(ignoreResponseSizes).orElse(Collections.emptyList()).contains(length);
    }

    /**
     * Checks if the numbers of words received in response are not ignored.
     *
     * @param words the number of words received in the response
     * @return true if the number of words in the response to not match the --ignoreResponseWords argument, false otherwise
     */
    public boolean isNotIgnoredResponseWords(long words) {
        return !Optional.ofNullable(ignoreResponseWords).orElse(Collections.emptyList()).contains(words);
    }

    /**
     * Checks if the numbers of lines received in response are not ignored.
     *
     * @param lines the number of lines received in the response
     * @return true if the number of lines in the response to not match the --ignoreResponseLines argument, false otherwise
     */
    public boolean isNotIgnoredResponseLines(long lines) {
        return !Optional.ofNullable(ignoreResponseLines).orElse(Collections.emptyList()).contains(lines);
    }

    /**
     * Checks the --ignoreResponseRegex against the response body.
     *
     * @param body the http response body
     * @return true if the regex is not found in the body, false otherwise
     */
    public boolean isNotIgnoredRegex(String body) {
        return !body.matches(Optional.ofNullable(ignoreResponseRegex).orElse("cats_body"));
    }

    /**
     * Check the response is not ignored based on response code, size, number of lines or number of words.
     *
     * @param catsResponse the response received from the service
     * @return true if the response should not be ignored, false otherwise
     */
    public boolean isNotIgnoredResponse(CatsResponse catsResponse) {
        return !this.isIgnoredResponseCode(catsResponse.responseCodeAsString()) &&
                this.isNotIgnoredResponseLength(catsResponse.getContentLengthInBytes()) &&
                this.isNotIgnoredResponseLines(catsResponse.getNumberOfLinesInResponse()) &&
                this.isNotIgnoredResponseWords(catsResponse.getNumberOfWordsInResponse()) &&
                this.isNotIgnoredRegex(catsResponse.getBody());
    }

    /**
     * Check the response is ignored based on response code, size, number of lines or number of words.
     *
     * @param catsResponse the response received from the service
     * @return true if the response should be ignored, false otherwise
     */
    public boolean isIgnoredResponse(CatsResponse catsResponse) {
        return !this.isNotIgnoredResponse(catsResponse);
    }
}
