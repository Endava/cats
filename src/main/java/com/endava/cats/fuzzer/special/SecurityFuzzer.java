package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@SpecialFuzzer
public class SecurityFuzzer implements CustomFuzzerBase {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;

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
        Map<String, Object> currentPathValues = this.getCurrentPathValues(data);
        if (currentPathValues != null) {
            currentPathValues.forEach((key, value) -> this.executeTestCases(data, key, value));
        } else {
            log.skip("Skipping path [{}] for method [{}] as it was not configured in securityFuzzerFile", data.getPath(), data.getMethod());
        }
    }

    public List<String> getMissingRequiredKeywords(Map<String, Object> currentTestCase) {
        List<String> missing = getRequiredKeywords().stream().filter(keyword -> currentTestCase.get(keyword) == null).collect(Collectors.toList());
        if (currentTestCase.get(CatsDSLWords.TARGET_FIELDS_TYPES) == null && currentTestCase.get(CatsDSLWords.TARGET_FIELDS) == null) {
            missing.add(CatsDSLWords.TARGET_FIELDS + " or " + CatsDSLWords.TARGET_FIELDS_TYPES);
        }
        return missing;
    }

    private List<String> getRequiredKeywords() {
        return List.of(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.DESCRIPTION, CatsDSLWords.STRINGS_FILE, CatsDSLWords.HTTP_METHOD);
    }

    private Map<String, Object> getCurrentPathValues(FuzzingData data) {
        Map<String, Object> currentPathValues = filesArguments.getSecurityFuzzerDetails().get(data.getPath());
        if (CollectionUtils.isEmpty(currentPathValues)) {
            currentPathValues = filesArguments.getSecurityFuzzerDetails().get(CatsDSLWords.ALL);
        }

        return currentPathValues;
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        log.info("Path [{}] has the following security configuration [{}]", data.getPath(), value);
        Map<String, Object> individualTestConfig = (Map<String, Object>) value;

        List<String> missingRequiredKeywords = this.getMissingRequiredKeywords(individualTestConfig);
        if (!missingRequiredKeywords.isEmpty()) {
            log.error("Path [{}] is missing the following mandatory entries: {}", data.getPath(), missingRequiredKeywords);
            return;
        }

        String stringsFile = String.valueOf(individualTestConfig.get(CatsDSLWords.STRINGS_FILE));

        try {
            log.start("Parsing stringsFile...");
            List<String> nastyStrings = Files.readAllLines(Paths.get(stringsFile));
            log.complete("stringsFile parsed successfully! Found {} entries", nastyStrings.size());
            List<String> targetFields = this.getTargetFields(individualTestConfig, data);
            this.fuzzFields(data, key, individualTestConfig, nastyStrings, targetFields);
        } catch (Exception e) {
            log.error("Invalid stringsFile [{}]", stringsFile, e);
        }
    }

    private void fuzzFields(FuzzingData data, String key, Map<String, Object> individualTestConfig, List<String> nastyStrings, List<String> targetFields) {
        log.debug("Target fields {}", targetFields);
        for (String targetField : targetFields) {
            log.info("Fuzzing field [{}]", targetField);
            Map<String, Object> individualTestConfigClone = individualTestConfig.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            individualTestConfigClone.put(targetField, nastyStrings);
            individualTestConfigClone.put(CatsDSLWords.DESCRIPTION, individualTestConfig.get(CatsDSLWords.DESCRIPTION) + ", field [" + targetField + "]");
            individualTestConfigClone.remove(CatsDSLWords.TARGET_FIELDS);
            individualTestConfigClone.remove(CatsDSLWords.TARGET_FIELDS_TYPES);
            individualTestConfigClone.remove(CatsDSLWords.STRINGS_FILE);
            customFuzzerUtil.executeTestCases(data, key, individualTestConfigClone, this);
        }
    }

    private List<String> getTargetFields(Map<String, Object> individualTestConfig, FuzzingData data) {
        String[] targetFields = this.extractListEntry(individualTestConfig, CatsDSLWords.TARGET_FIELDS);
        String[] targetFieldsTypes = this.extractListEntry(individualTestConfig, CatsDSLWords.TARGET_FIELDS_TYPES);

        List<String> catsFields = data.getAllFieldsAsCatsFields().stream()
                .filter(catsField -> Arrays.asList(targetFieldsTypes).contains(StringUtils.toRootLowerCase(catsField.getSchema().getType()))
                        || Arrays.asList(targetFieldsTypes).contains(StringUtils.toRootLowerCase(catsField.getSchema().getFormat())))
                .map(CatsField::getName)
                .filter(name -> !JsonUtils.getVariableFromJson(data.getPayload(), name).equals(JsonUtils.NOT_SET))
                .collect(Collectors.toList());

        /*we also add the HTTP headers in this list. the actual split logic will be handled in CustomFuzzerUtil*/
        catsFields.add(Arrays.stream(targetFieldsTypes).filter(type -> type.equalsIgnoreCase(CatsDSLWords.CATS_HEADERS)).findFirst().orElse(""));
        catsFields.addAll(Arrays.asList(targetFields));
        catsFields.removeIf(StringUtils::isBlank);

        return catsFields;
    }

    private String[] extractListEntry(Map<String, Object> individualTestConfig, String key) {
        return String.valueOf(individualTestConfig.getOrDefault(key, "")).replace("[", "")
                .replace(" ", "").replace("]", "").split(",");
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "use custom dictionaries of 'nasty' strings to target specific fields or data types";
    }

    @Override
    public List<String> reservedWords() {
        return Arrays.asList(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.DESCRIPTION, CatsDSLWords.OUTPUT, CatsDSLWords.VERIFY, CatsDSLWords.STRINGS_FILE, CatsDSLWords.TARGET_FIELDS,
                CatsDSLWords.TARGET_FIELDS_TYPES, CatsDSLWords.MAP_VALUES, CatsDSLWords.ONE_OF_SELECTION, CatsDSLWords.ADDITIONAL_PROPERTIES, CatsDSLWords.ELEMENT, CatsDSLWords.HTTP_METHOD);
    }
}
