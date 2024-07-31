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
import java.util.regex.Pattern;

/**
 * Holds arguments to match against service responses. They are typically used with the Template and UserDictionary fuzzers.
 */
@Singleton
@Getter
public class MatchArguments {
    @CommandLine.Option(names = {"--matchResponseCodes", "--mc"},
            description = "A comma separated list of HTTP response codes that will be matched as @|bold,underline error|@. All other response codes will be ignored from the final report", split = ",")
    private List<String> matchResponseCodes;

    @CommandLine.Option(names = {"--matchResponseSize", "--ms"},
            description = "A comma separated list of response sizes that will be matched as @|bold,underline error|@. All other response sizes will be ignored from the final report", split = ",")
    private List<Long> matchResponseSizes;

    @CommandLine.Option(names = {"--matchResponseWords", "--mw"},
            description = "A comma separated list of word counts in the response that will be matched as @|bold,underline error|@. All other response word counts will be ignored from the final report", split = ",")
    private List<Long> matchResponseWords;

    @CommandLine.Option(names = {"--matchResponseLines", "--ml"},
            description = "A comma separated list of number of line counts in the response that will be matched as @|bold,underline error|@. All other response line counts will be ignored from the final report", split = ",")
    private List<Long> matchResponseLines;

    @CommandLine.Option(names = {"--matchResponseRegex", "--mr"},
            description = "A regex that will match against the response that will be matched as @|bold,underline error|@. Non-matching responses will be ignored from the final report")
    private String matchResponseRegex;

    @CommandLine.Option(names = {"--matchInput", "--mi"},
            description = "A flag to check if the response is reflecting the fuzzed value that will match as @|bold,underline error|@. Non-matching responses will be ignored from the final report. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    private boolean matchInput;

    /**
     * Checks if any matching argument (response codes, sizes, words, lines, or regex) has been supplied.
     *
     * @return {@code true} if any matching argument is supplied, {@code false} otherwise.
     */
    public boolean isAnyMatchArgumentSupplied() {
        return matchResponseCodes != null || matchResponseSizes != null
                || matchResponseWords != null || matchResponseLines != null
                || matchResponseRegex != null || matchInput;
    }

    /**
     * Checks if the specified response code is present in the list of matched response codes.
     *
     * @param responseCode The response code to check for in the list of matched response codes.
     * @return {@code true} if the response code is present in the list and is a match, {@code false} otherwise.
     */
    public boolean isMatchedResponseCode(String responseCode) {
        return StringUtils.isNotBlank(responseCode) &&
                this.getMatchResponseCodes().stream().anyMatch(code -> ResponseCodeFamily.matchAsCodeOrRange(code, responseCode));
    }

    /**
     * Checks if the specified size is present in the list of matched response sizes.
     *
     * @param size The size to check for in the list of matched response sizes.
     * @return {@code true} if the size is present in the list, {@code false} otherwise.
     */
    public boolean isMatchedResponseSize(long size) {
        return Optional.ofNullable(matchResponseSizes).orElse(Collections.emptyList()).contains(size);
    }

    /**
     * Checks if the specified count is present in the list of matched response words.
     *
     * @param count The count to check for in the list of matched response words.
     * @return {@code true} if the count is present in the list, {@code false} otherwise.
     */
    public boolean isMatchedResponseWords(long count) {
        return Optional.ofNullable(matchResponseWords).orElse(Collections.emptyList()).contains(count);
    }

    /**
     * Checks if the specified count is present in the list of matched response lines.
     *
     * @param count The count to check for in the list of matched response lines.
     * @return {@code true} if the count is present in the list, {@code false} otherwise.
     */
    public boolean isMatchedResponseLines(long count) {
        return Optional.ofNullable(matchResponseLines).orElse(Collections.emptyList()).contains(count);
    }

    /**
     * Checks if the given string matches the {@code --matchResponseRegex}.
     *
     * @param body the response body
     * @return true if the regex matches the body, false otherwise
     */
    public boolean isMatchedResponseRegex(String body) {
        if (StringUtils.isBlank(body) && matchResponseRegex == null) {
            return false;
        }
        Pattern pattern = Pattern.compile(Optional.ofNullable(matchResponseRegex).orElse(""), Pattern.DOTALL);
        return pattern.matcher(body).matches();
    }

    /**
     * Returns the list of {@code --matchResponseCodes} or an empty list otherwise
     *
     * @return response codes to match
     */
    public List<String> getMatchResponseCodes() {
        return Optional.ofNullable(matchResponseCodes).orElse(Collections.emptyList());
    }


    /**
     * Checks if the given response matches any of the {@code --matchXXX arguments}.
     *
     * @param response the service response
     * @return true if the response matches any of the arguments, false otherwise
     */
    public boolean isMatchResponse(CatsResponse response) {
        return isMatchedResponseCode(response.responseCodeAsString()) ||
                isMatchedResponseWords(response.getNumberOfWordsInResponse()) ||
                isMatchedResponseLines(response.getNumberOfLinesInResponse()) ||
                isMatchedResponseSize(response.getContentLengthInBytes()) ||
                isMatchedResponseRegex(response.getBody());
    }

    /**
     * Checks if the provided input value is reflected in the body of a Cats response.
     * Check is done only if {@code --matchInput} is supplied.
     *
     * @param catsResponse the Cats response to analyze
     * @param inputValue   the input value to check for reflection
     * @return {@code true} if the input value is reflected in the response body, {@code false} otherwise
     */
    public boolean isInputReflected(CatsResponse catsResponse, Object inputValue) {
        return matchInput && catsResponse.getBody().contains(String.valueOf(inputValue));
    }

    /**
     * Generates a string representation of the matching criteria based on the configured parameters.
     *
     * @return A string representation of the matching criteria, excluding empty parameters.
     */
    public String getMatchString() {
        StringBuilder builder = new StringBuilder();
        if (!this.getMatchResponseCodes().isEmpty()) {
            builder.append(", response codes: ").append(this.getMatchResponseCodes());
        }
        if (this.matchResponseRegex != null) {
            builder.append(", regex: ").append(this.matchResponseRegex);
        }
        if (this.matchResponseLines != null) {
            builder.append(", number of lines: ").append(this.getMatchResponseLines());
        }
        if (this.matchResponseWords != null) {
            builder.append(", number of words: ").append(this.getMatchResponseWords());
        }
        if (this.matchResponseSizes != null) {
            builder.append(", response sizes: ").append(this.getMatchResponseSizes());
        }

        return StringUtils.stripStart(builder.toString(), ",");
    }
}
