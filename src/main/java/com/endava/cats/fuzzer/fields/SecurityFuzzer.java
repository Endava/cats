package com.endava.cats.fuzzer.fields;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.endava.cats.util.CustomFuzzerUtil.*;

@Component
@Slf4j
@FieldFuzzer
public class SecurityFuzzer implements CustomFuzzerBase {

    private final CatsUtil catsUtil;
    private final CustomFuzzerUtil customFuzzerUtil;

    @Value("${securityFuzzerFile:empty}")
    private String securityFuzzerFile;

    private Map<String, Map<String, Object>> securityFuzzerDetails = new HashMap<>();

    @Autowired
    public SecurityFuzzer(CatsUtil cu, CustomFuzzerUtil cfu) {
        this.catsUtil = cu;
        this.customFuzzerUtil = cfu;
    }

    @PostConstruct
    public void loadSecurityFuzzerFile() {
        try {
            if (CatsMain.EMPTY.equalsIgnoreCase(securityFuzzerFile)) {
                log.info("No security custom Fuzzer file. SecurityFuzzer will be skipped!");
            } else {
                securityFuzzerDetails = catsUtil.parseYaml(securityFuzzerFile);
            }
        } catch (Exception e) {
            log.error("Error processing securityFuzzerFile!", e);
        }
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!securityFuzzerDetails.isEmpty()) {
            this.processSecurityFuzzerFile(data);
        }
    }

    protected void processSecurityFuzzerFile(FuzzingData data) {
        Map<String, Object> currentPathValues = securityFuzzerDetails.get(data.getPath());
        if (currentPathValues != null) {
            currentPathValues.forEach((key, value) -> this.executeTestCases(data, key, value));
        } else {
            log.info("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
        }
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        log.info("Path [{}] has the following security configuration [{}]", data.getPath(), value);
        Map<String, Object> individualTestConfig = (Map<String, Object>) value;
        String stringsFile = String.valueOf(individualTestConfig.get(STRINGS_FILE));

        try {
            List<String> nastyStrings = Files.readAllLines(Paths.get(stringsFile));
            log.info("Parsing stringsFile...");
            log.info("stringsFile parsed successfully! Found {} entries", nastyStrings.size());
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
