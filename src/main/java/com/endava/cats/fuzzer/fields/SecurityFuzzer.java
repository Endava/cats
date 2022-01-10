package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.dsl.CatsDSLWords;
import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.fuzzer.CustomFuzzerUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

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
        Map<String, Object> currentPathValues = filesArguments.getSecurityFuzzerDetails().get(data.getPath());
        if (currentPathValues != null) {
            currentPathValues.forEach((key, value) -> this.executeTestCases(data, key, value));
        } else {
            log.skip("Skipping path [{}] as it was not configured in securityFuzzerFile", data.getPath());
        }
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        log.info("Path [{}] has the following security configuration [{}]", data.getPath(), value);
        Map<String, Object> individualTestConfig = (Map<String, Object>) value;
        String stringsFile = String.valueOf(individualTestConfig.get(CatsDSLWords.STRINGS_FILE));

        try {
            List<String> nastyStrings = Files.readAllLines(Paths.get(stringsFile));
            log.start("Parsing stringsFile...");
            log.complete("stringsFile parsed successfully! Found {} entries", nastyStrings.size());
            String[] targetFields = String.valueOf(individualTestConfig.get(CatsDSLWords.TARGET_FIELDS)).replace("[", "")
                    .replace(" ", "").replace("]", "").split(",");

            for (String targetField : targetFields) {
                log.info("Fuzzing field [{}]", targetField);
                Map<String, Object> individualTestConfigClone = individualTestConfig.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                individualTestConfigClone.put(targetField, nastyStrings);
                individualTestConfigClone.put(CatsDSLWords.DESCRIPTION, individualTestConfig.get(CatsDSLWords.DESCRIPTION) + ", field [" + targetField + "]");
                individualTestConfigClone.remove(CatsDSLWords.TARGET_FIELDS);
                individualTestConfigClone.remove(CatsDSLWords.STRINGS_FILE);
                customFuzzerUtil.executeTestCases(data, key, individualTestConfigClone, this);
            }

        } catch (Exception e) {
            log.error("Invalid stringsFile [{}]", stringsFile, e);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName().replace("_Subclass", "");
    }

    @Override
    public String description() {
        return "send 'nasty' strings intended to crash the service";
    }

    @Override
    public List<String> reservedWords() {
        return Arrays.asList(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.DESCRIPTION, CatsDSLWords.OUTPUT, CatsDSLWords.VERIFY, CatsDSLWords.STRINGS_FILE, CatsDSLWords.TARGET_FIELDS,
                CatsDSLWords.MAP_VALUES, CatsDSLWords.ONE_OF_SELECTION, CatsDSLWords.ADDITIONAL_PROPERTIES, CatsDSLWords.ELEMENT, CatsDSLWords.HTTP_METHOD);
    }
}
