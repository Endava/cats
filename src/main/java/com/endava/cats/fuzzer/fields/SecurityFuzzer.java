package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.SpecialFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CustomFuzzerUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.endava.cats.util.CustomFuzzerUtil.*;

@Component
@SpecialFuzzer
public class SecurityFuzzer implements CustomFuzzerBase {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;

    @Autowired
    public SecurityFuzzer(FilesArguments cp, CustomFuzzerUtil cfu) {
        this.filesArguments = cp;
        this.customFuzzerUtil = cfu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!filesArguments.getSecurityFuzzerDetails().isEmpty()) {
            this.processSecurityFuzzerFile(data);
        }
    }

    protected void processSecurityFuzzerFile(FuzzingData data) {
        Map<String, Object> currentPathValues = filesArguments.getSecurityFuzzerDetails().get(data.getPath());
        if (currentPathValues != null) {
            currentPathValues.forEach((key, value) -> this.executeTestCases(data, key, value));
        } else {
            log.skip("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
        }
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        log.info("Path [{}] has the following security configuration [{}]", data.getPath(), value);
        Map<String, Object> individualTestConfig = (Map<String, Object>) value;
        String stringsFile = String.valueOf(individualTestConfig.get(STRINGS_FILE));

        try {
            List<String> nastyStrings = Files.readAllLines(Paths.get(stringsFile));
            log.start("Parsing stringsFile...");
            log.complete("stringsFile parsed successfully! Found {} entries", nastyStrings.size());
            String[] targetFields = String.valueOf(individualTestConfig.get(TARGET_FIELDS)).replace("[", "")
                    .replace(" ", "").replace("]", "").split(",");

            for (String targetField : targetFields) {
                log.info("Fuzzing field [{}]", targetField);
                Map<String, Object> individualTestConfigClone = individualTestConfig.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                individualTestConfigClone.put(targetField, nastyStrings);
                individualTestConfigClone.put(DESCRIPTION, individualTestConfig.get(DESCRIPTION) + ", field [" + targetField + "]");
                individualTestConfigClone.remove(TARGET_FIELDS);
                individualTestConfigClone.remove(STRINGS_FILE);
                customFuzzerUtil.executeTestCases(data, key, individualTestConfigClone, this);
            }

        } catch (Exception e) {
            log.error("Invalid stringsFile [{}]", stringsFile, e);
        }
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "send 'nasty' strings intended to crash the service";
    }

    @Override
    public List<String> reservedWords() {
        return Arrays.asList(CustomFuzzerUtil.EXPECTED_RESPONSE_CODE, CustomFuzzerUtil.DESCRIPTION, CustomFuzzerUtil.OUTPUT, CustomFuzzerUtil.VERIFY, STRINGS_FILE, TARGET_FIELDS,
                CustomFuzzerUtil.MAP_VALUES, CustomFuzzerUtil.ONE_OF_SELECTION, CustomFuzzerUtil.ADDITIONAL_PROPERTIES, CustomFuzzerUtil.ELEMENT, CustomFuzzerUtil.HTTP_METHOD);
    }
}
