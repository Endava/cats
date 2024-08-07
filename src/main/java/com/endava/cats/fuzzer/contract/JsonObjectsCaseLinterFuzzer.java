package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.factory.NoMediaType;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks if JSON objects are consistently following the same naming convention.
 */
@LinterFuzzer
@Singleton
public class JsonObjectsCaseLinterFuzzer extends BaseLinterFuzzer {
    private static final Pattern GENERATED_BODY_OBJECTS_1 = Pattern.compile("body_\\d*");
    private static final Pattern GENERATED_BODY_OBJECTS_2 = Pattern.compile("^\\w{1,30}_body");
    private static final Pattern INLINE_BODY_OBJECTS = Pattern.compile("^inline_response(?:_\\d{0,30}){1,50}$");
    private static final Pattern CROSS_PATHS_REFERENCES_PATTERN = Pattern.compile("^#/paths/.*");
    private static final List<Pattern> PATTERNS_TO_IGNORE = List.of(GENERATED_BODY_OBJECTS_1, GENERATED_BODY_OBJECTS_2, INLINE_BODY_OBJECTS, CROSS_PATHS_REFERENCES_PATTERN);
    private static final Set<String> PROPERTIES_CHECKED = new HashSet<>();
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final ProcessingArguments processingArguments;
    private final NamingArguments namingArguments;

    /**
     * Creates a new JsonObjectsCaseLinterFuzzer instance.
     *
     * @param tcl      the test case listener
     * @param proc     used to retrieve any user provided content type
     * @param nameArgs used to get the naming convention
     */
    public JsonObjectsCaseLinterFuzzer(TestCaseListener tcl, ProcessingArguments proc, NamingArguments nameArgs) {
        super(tcl);
        this.processingArguments = proc;
        this.namingArguments = nameArgs;
    }

    @Override
    public void process(FuzzingData data) {
        PROPERTIES_CHECKED.clear();
        String expectedResult = "JSON objects and properties must follow naming conventions: JSON objects %s, JSON properties %s"
                .formatted(namingArguments.getJsonObjectsNaming().getDescription(), namingArguments.getJsonPropertiesNaming().getDescription());
        testCaseListener.addScenario(log, "Check if the JSON elements follow naming conventions: JSON objects {}, JSON properties {}",
                namingArguments.getJsonObjectsNaming().getDescription(), namingArguments.getJsonPropertiesNaming().getDescription());
        testCaseListener.addExpectedResult(log, expectedResult);

        StringBuilder errorString = new StringBuilder();

        errorString.append(" JSON objects %s: ".formatted(namingArguments.getJsonObjectsNaming().getDescription()));
        String jsonObjectsError = this.checkJsonObjects(data);
        boolean hasErrors = this.hasErrors(jsonObjectsError);
        errorString.append(jsonObjectsError);

        String jsonPropertiesError = this.checkJsonProperties(data);
        hasErrors = hasErrors || !jsonPropertiesError.isBlank();
        errorString.append("; JSON properties %s: ".formatted(namingArguments.getJsonPropertiesNaming().getDescription()));
        errorString.append(jsonPropertiesError);

        if (hasErrors) {
            testCaseListener.reportResultError(log, data, "JSON elements do not follow recommended naming",
                    "JSON elements do not follow naming conventions: {}", StringUtils.stripEnd(errorString.toString().trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "JSON elements follow naming conventions.");
        }
    }

    private String checkJsonProperties(FuzzingData data) {
        Set<CatsField> catsFields = data.getAllFieldsAsCatsFields();
        StringBuilder result = new StringBuilder();
        for (CatsField catsField : catsFields) {
            String[] props = catsField.getName().split("#", -1);
            String propertyToCheck = props[props.length - 1];
            if (!namingArguments.getJsonPropertiesNaming().getPattern().matcher(propertyToCheck).matches() && !PROPERTIES_CHECKED.contains(catsField.getName())) {
                PROPERTIES_CHECKED.add(catsField.getName());
                result.append(catsField.getName()).append(", ");
            }
        }
        return result.toString();
    }

    private String checkJsonObjects(FuzzingData data) {
        List<String> stringToCheck = new ArrayList<>();
        stringToCheck.add(data.getReqSchemaName());
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        for (ApiResponse apiResponse : operation.getResponses().values()) {
            String ref = apiResponse.get$ref();
            if (ref == null && apiResponse.getContent() != null) {
                MediaType mediaType = OpenApiUtils.getMediaTypeFromContent(apiResponse.getContent(), processingArguments.getDefaultContentType());
                if (mediaType != null) {
                    ref = mediaType.getSchema().get$ref();
                }
            }

            if (ref != null) {
                stringToCheck.add(ref.substring(ref.lastIndexOf("/") + 1));
            }

        }
        return CatsUtil.check(stringToCheck.toArray(new String[0]), jsonObject ->
                !namingArguments.getJsonObjectsNaming().getPattern().matcher(jsonObject).matches()
                        && PATTERNS_TO_IGNORE.stream().noneMatch(pattern -> pattern.matcher(jsonObject).matches())
                        && !NoMediaType.EMPTY_BODY.matches(jsonObject));
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "verifies that JSON elements follow naming conventions";
    }
}