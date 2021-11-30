package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Getter
public class IgnoreArguments {
    private static final String EMPTY = "empty";
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(IgnoreArguments.class);
    private final List<CatsArg> args = new ArrayList<>();

    private final String ignoreResponseCodeUndocumentedCheckHelp = "If supplied (not value needed) it won't check if the response code received from the service matches the value expected by the fuzzer and will return the test result as SUCCESS instead of WARN";
    private final String ignoreResponseBodyCheckHelp = "If supplied (not value needed) it won't check if the response body received from the service matches the schema supplied inside the contract and will return the test result as SUCCESS instead of WARN";
    private final String ignoreResponseCodesHelp = "HTTP_CODES_LIST a comma separated list of HTTP response codes that will be considered as SUCCESS, even if the Fuzzer will typically report it as WARN or ERROR. If provided, all Contract Fuzzers will be skipped";
    private final String skipFieldsHelp = "field1,field2#subField1 a comma separated list of fields that will be skipped by replacement Fuzzers like EmptyStringsInFields, NullValuesInFields, etc.";
    private final String blackboxHelp = "If supplied (not value needed) it will ignore all response codes expect for 5XX which will be returned as ERROR. This is similar to --ignoreResponseCodes=\"2xx,4xx\"";

    @Value("${ignoreResponseCodeUndocumentedCheck:empty}")
    private String ignoreResponseCodeUndocumentedCheck;
    @Value("${ignoreResponseBodyCheck:empty}")
    private String ignoreResponseBodyCheck;
    @Value("${ignoreResponseCodes:empty}")
    private String ignoreResponseCodes;
    @Value("${skipFields:empty}")
    private String skipFields;
    @Value("${blackbox:empty}")
    private String blackbox;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("ignoreResponseCodes").value(ignoreResponseCodes).help(ignoreResponseCodesHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseCodeUndocumentedCheck").value(ignoreResponseCodeUndocumentedCheck).help(ignoreResponseCodeUndocumentedCheckHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseBodyCheck").value(ignoreResponseBodyCheck).help(ignoreResponseBodyCheckHelp).build());
        args.add(CatsArg.builder().name("skipFields").value(skipFields).help(skipFieldsHelp).build());
        args.add(CatsArg.builder().name("blackbox").value(blackbox).help(blackboxHelp).build());
    }

    public List<String> getIgnoreResponseCodes() {
        if (isEmptyIgnoredResponseCodes()) {
            return Collections.emptyList();
        }

        return Arrays.stream(ignoreResponseCodes.split(",")).map(code -> code.trim().strip())
                .collect(Collectors.toList());
    }

    public boolean isEmptyIgnoredResponseCodes() {
        return !CatsUtil.isArgumentValid(ignoreResponseCodes);
    }

    public boolean isIgnoredResponseCode(String receivedResponseCode) {
        return getIgnoreResponseCodes().stream()
                .anyMatch(code -> code.equalsIgnoreCase(receivedResponseCode)
                        || (code.substring(1, 3).equalsIgnoreCase("xx") && code.substring(0, 1).equalsIgnoreCase(receivedResponseCode.substring(0, 1))));

    }

    public List<String> getSkippedFields() {
        return Arrays.stream(skipFields.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    public boolean isIgnoreResponseCodeUndocumentedCheck() {
        return !EMPTY.equalsIgnoreCase(ignoreResponseCodeUndocumentedCheck);
    }

    public boolean isIgnoreResponseBodyCheck() {
        return !EMPTY.equalsIgnoreCase(ignoreResponseBodyCheck);
    }

    public boolean isBlackbox() {
        return !EMPTY.equalsIgnoreCase(blackbox);
    }
}
