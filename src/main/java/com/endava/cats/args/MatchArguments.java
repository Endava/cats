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

@Singleton
@Getter
public class MatchArguments {
    @CommandLine.Option(names = {"--matchResponseCodes", "--mc"},
            description = "A comma separated list of HTTP response codes that will be matched as @|bold,underline error|@. All other response codes will be ignored from the final report. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<String> matchResponseCodes;

    @CommandLine.Option(names = {"--matchResponseSize", "--ms"},
            description = "A comma separated list of response sizes that will be matched as @|bold,underline error|@. All other response sizes will be ignored from the final report. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<Long> matchResponseSizes;

    @CommandLine.Option(names = {"--matchResponseWords", "--mw"},
            description = "A comma separated list of word counts in the response that will be matched as @|bold,underline error|@. All other response word counts will be ignored from the final report. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<Long> matchResponseWords;

    @CommandLine.Option(names = {"--matchResponseLines", "--ml"},
            description = "A comma separated list of number of line counts in the response that will be matched as @|bold,underline error|@. All other response line counts will be ignored from the final report. If provided, all Contract Fuzzers will be skipped", split = ",")
    private List<Long> matchResponseLines;

    @CommandLine.Option(names = {"--matchResponseRegex", "--mr"},
            description = "A regex that will match against the response that will be matched as @|bold,underline error|@. All other response body matches will be ignored from the final report. If provided, all Contract Fuzzers will be skipped")
    private String matchResponseRegex;


    public boolean isAnyMatchArgumentSupplied() {
        return matchResponseCodes != null || matchResponseSizes != null
                || matchResponseWords != null || matchResponseLines != null
                || matchResponseRegex != null;
    }

    public boolean isMatchedResponseCode(String responseCode) {
        return StringUtils.isNotBlank(responseCode) &&
                this.getMatchResponseCodes().stream().anyMatch(code -> ResponseCodeFamily.matchAsCodeOrRange(code, responseCode));
    }

    public boolean isMatchedResponseSize(long size) {
        return Optional.ofNullable(matchResponseSizes).orElse(Collections.emptyList()).contains(size);
    }

    public boolean isMatchedResponseWords(long count) {
        return Optional.ofNullable(matchResponseWords).orElse(Collections.emptyList()).contains(count);
    }

    public boolean isMatchedResponseLines(long count) {
        return Optional.ofNullable(matchResponseLines).orElse(Collections.emptyList()).contains(count);
    }

    public boolean isMatchedResponseRegex(String body) {
        return body.matches(Optional.ofNullable(matchResponseRegex).orElse(""));
    }

    public List<String> getMatchResponseCodes() {
        return Optional.ofNullable(matchResponseCodes).orElse(Collections.emptyList());
    }

    public boolean isMatchResponse(CatsResponse response) {
        return isMatchedResponseCode(response.responseCodeAsString()) ||
                isMatchedResponseWords(response.getNumberOfWordsInResponse()) ||
                isMatchedResponseLines(response.getNumberOfLinesInResponse()) ||
                isMatchedResponseSize(response.getContentLengthInBytes()) ||
                isMatchedResponseRegex(response.getBody());
    }
}
