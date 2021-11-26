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
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(IgnoreArguments.class);
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${ignoreResponseCodeUndocumentedCheck:empty}")
    private String ignoreResponseCodeUndocumentedCheck;
    @Value("${ignoreResponseBodyCheck:empty}")
    private String ignoreResponseBodyCheck;
    @Value("${ignoreResponseCodes:empty}")
    private String ignoreResponseCodes;
    @Value("${skipFields:empty}")
    private String skipFields;

    @Value("${arg.filter.ignoreResponseCodeUndocumentedCheck.help:help}")
    private String ignoreResponseCodeUndocumentedCheckHelp;
    @Value("${arg.filter.ignoreResponseBodyCheck.help:help}")
    private String ignoreResponseBodyCheckHelp;
    @Value("${arg.filter.ignoreResponseCodes.help:help}")
    private String ignoreResponseCodesHelp;
    @Value("${arg.filter.skipFields.help:help}")
    private String skipFieldsHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("ignoreResponseCodes").value(ignoreResponseCodes).help(ignoreResponseCodesHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseCodeUndocumentedCheck").value(ignoreResponseCodeUndocumentedCheck).help(ignoreResponseCodeUndocumentedCheckHelp).build());
        args.add(CatsArg.builder().name("ignoreResponseBodyCheck").value(ignoreResponseBodyCheck).help(ignoreResponseBodyCheckHelp).build());
        args.add(CatsArg.builder().name("skipFields").value(skipFields).help(skipFieldsHelp).build());
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
        return !"empty".equalsIgnoreCase(ignoreResponseCodeUndocumentedCheck);
    }

    public boolean isIgnoreResponseBodyCheck() {
        return !"empty".equalsIgnoreCase(ignoreResponseBodyCheck);
    }
}
