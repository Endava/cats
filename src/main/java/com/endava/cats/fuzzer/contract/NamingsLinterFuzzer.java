package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.factory.NoMediaType;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@LinterFuzzer
@Singleton
public class NamingsLinterFuzzer extends BaseLinterFuzzer {
    private static final Pattern GENERATED_BODY_OBJECTS = Pattern.compile("body_\\d*");
    private static final List<String> VERSIONS = Arrays.asList("version\\d*\\.?", "v\\d+\\.?");

    private static final Set<String> PROPERTIES_CHECKED = new HashSet<>();
    private static final String PLURAL_END = "s";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final ProcessingArguments processingArguments;
    private final NamingArguments namingArguments;

    public NamingsLinterFuzzer(TestCaseListener tcl, ProcessingArguments proc, NamingArguments nameArgs) {
        super(tcl);
        this.processingArguments = proc;
        this.namingArguments = nameArgs;
    }

    @Override
    public void process(FuzzingData data) {
        String expectedResult = "Path should follow the RESTful API naming good practices. " +
                "Must use the following naming conventions: nouns, plurals, paths %s, path variables %s, query params %s, JSON objects %s, JSON properties %s, headers %s"
                        .formatted(namingArguments.getPathNaming().getDescription(), namingArguments.getPathVariablesNaming().getDescription(),
                                namingArguments.getQueryParamsNaming().getDescription(), namingArguments.getJsonObjectsNaming().getDescription(),
                                namingArguments.getJsonPropertiesNaming().getDescription(), namingArguments.getHeadersNaming().getDescription());
        testCaseListener.addScenario(log, "Check if the path {} follows RESTful API naming good practices for HTTP method {}", data.getPath(), data.getMethod());
        testCaseListener.addExpectedResult(log, expectedResult);

        StringBuilder errorString = new StringBuilder();
        String[] pathElements = Arrays.stream(data.getPath().substring(1).split("/"))
                .filter(pathElement -> VERSIONS
                        .stream()
                        .noneMatch(version -> Pattern.compile(version).matcher(pathElement).matches()))
                .toArray(String[]::new);

        errorString.append(this.checkPlurals(pathElements));
        errorString.append(this.checkPathElements(pathElements));
        errorString.append(this.checkPathVariables(pathElements));
        if (HttpMethod.requiresBody(data.getMethod().name())) {
            errorString.append(this.checkJsonObjects(data));
            errorString.append(this.checkJsonProperties(data));
        }
        errorString.append(this.checkHeaders(data));
        errorString.append(this.checkQueryParams(data));

        if (!errorString.toString().isEmpty()) {
            testCaseListener.reportResultError(log, data, "Paths not following recommended naming",
                    "Path does not follow RESTful API naming good practices: {}", StringUtils.stripEnd(errorString.toString().trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path follows the RESTful API naming good practices.");
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
                result.append("JSON properties not matching %s: %s, ".formatted(namingArguments.getJsonPropertiesNaming().getDescription(), catsField.getName()));
            }
        }
        return result.toString();
    }

    private String checkQueryParams(FuzzingData data) {
        return this.check(Optional.ofNullable(data.getQueryParams()).orElse(Collections.emptySet()).toArray(new String[0]), queryParam ->
                        !namingArguments.getQueryParamsNaming().getPattern().matcher(queryParam).matches(),
                "query parameters not matching %s: %s, ", namingArguments.getQueryParamsNaming().getDescription());
    }

    private String checkHeaders(FuzzingData data) {
        Set<CatsHeader> headers = data.getHeaders();
        return this.check(headers.stream().map(CatsHeader::getName).toArray(String[]::new), header ->
                        !namingArguments.getHeadersNaming().getPattern().matcher(header).matches(),
                "headers not matching %s: %s, ", namingArguments.getHeadersNaming().getDescription());
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
        return this.check(stringToCheck.toArray(new String[0]), jsonObject -> !namingArguments.getJsonObjectsNaming().getPattern().matcher(jsonObject).matches()
                        && !GENERATED_BODY_OBJECTS.matcher(jsonObject).matches() && !NoMediaType.EMPTY_BODY.matches(jsonObject),
                "JSON objects not matching %s: %s, ", namingArguments.getJsonObjectsNaming().getDescription());
    }

    private String checkPathVariables(String[] pathElements) {
        return this.check(pathElements, pathElement -> this.isAPathVariable(pathElement)
                        && !namingArguments.getPathVariablesNaming().getPattern().matcher(pathElement.replace("{", "").replace("}", "")).matches(),
                "path variables not matching %s: %s, ", namingArguments.getPathVariablesNaming().getDescription());
    }

    private String checkPathElements(String[] pathElements) {
        return this.check(pathElements, pathElement -> this.isNotAPathVariable(pathElement)
                        && !namingArguments.getPathNaming().getPattern().matcher(pathElement).matches(),
                "path elements not matching %s: %s, ", namingArguments.getPathNaming().getDescription());
    }

    private String checkPlurals(String[] pathElements) {
        String[] strippedPathElements = pathElements.length >= 2 ? Arrays.copyOfRange(pathElements, 1, pathElements.length - 1) : pathElements;

        return this.check(strippedPathElements, pathElement -> this.isNotAPathVariable(pathElement) && !pathElement.endsWith(PLURAL_END),
                "path elements not using %s: %s, ", "plural");
    }

    private String check(String[] pathElements, Predicate<String> checkFunction, String errorMessage, String convention) {
        StringBuilder result = new StringBuilder();

        for (String pathElement : pathElements) {
            if (checkFunction.test(pathElement)) {
                result.append(COMMA).append(pathElement);
            }
        }

        if (!result.toString().isEmpty()) {
            return String.format(errorMessage, convention, StringUtils.stripStart(result.toString().trim(), ", "));
        }

        return EMPTY;
    }

    private boolean isNotAPathVariable(String pathElement) {
        return !this.isAPathVariable(pathElement);
    }

    private boolean isAPathVariable(String pathElement) {
        return pathElement.startsWith("{");
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract elements follow RESTful API naming good practices";
    }
}
