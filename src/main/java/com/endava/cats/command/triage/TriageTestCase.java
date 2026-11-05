package com.endava.cats.command.triage;

import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestProvenance;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A report test case ({@code TestN.json}) paired with its execution number and convenience accessors used by triage.
 */
record TriageTestCase(int number, CatsTestCase testCase) {

    String method() {
        return Objects.requireNonNullElse(testCase.getRequest().getHttpMethod(), "").toUpperCase(Locale.ROOT);
    }

    String path() {
        return Objects.requireNonNullElse(testCase.getContractPath(), Objects.requireNonNullElse(testCase.getPath(), ""));
    }

    String operation() {
        return method() + " " + path();
    }

    int statusCode() {
        return testCase.getResponse().getResponseCode();
    }

    String fuzzer() {
        return testCase.getFuzzer();
    }

    boolean isFuzzer(Class<?> fuzzerClass) {
        return ConsoleUtils.matchesFuzzer(testCase.getFuzzer(), fuzzerClass);
    }

    ExpectedCodes expected() {
        return ExpectedCodes.parse(testCase.getExpectedResult());
    }

    boolean isError() {
        return "error".equalsIgnoreCase(testCase.getResult());
    }

    boolean isWarning() {
        return "warn".equalsIgnoreCase(testCase.getResult()) || "warning".equalsIgnoreCase(testCase.getResult());
    }

    String payload() {
        return testCase.getRequest().getPayload();
    }

    String url() {
        return Objects.requireNonNullElse(testCase.getRequest().getUrl(), Objects.requireNonNullElse(testCase.getFullRequestPath(), ""));
    }

    List<RequestTarget> mutationTargets() {
        return Optional.ofNullable(testCase.getRequestProvenance())
                .map(RequestProvenance::getMutationTargets)
                .orElse(List.of());
    }

    List<ResourceCorrelation> runtimeCorrelations() {
        return Optional.ofNullable(testCase.getRequestProvenance())
                .map(RequestProvenance::getRuntimeCorrelations)
                .orElse(List.of());
    }

    boolean isPathMutated(String pathParameter) {
        return mutationTargets().stream()
                .anyMatch(target -> target.location() == RequestTarget.Location.PATH && target.name().equals(pathParameter));
    }

    /**
     * Returns the request body as a JSON object; for root arrays, the first object element.
     */
    Optional<JsonObject> payloadRootObject() {
        String payload = payload();
        if (StringUtils.isBlank(payload) || !JsonUtils.isValidJson(payload)) {
            return Optional.empty();
        }
        JsonElement root = JsonUtils.parseAsJsonElement(payload);
        if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()) {
            root = root.getAsJsonArray().get(0);
        }
        return root.isJsonObject() ? Optional.of(root.getAsJsonObject()) : Optional.empty();
    }

    boolean hasRootArrayPayload() {
        return StringUtils.trimToEmpty(payload()).startsWith("[");
    }

    /**
     * Returns the response as JSON. Reports persist bodies in {@code jsonBody}; non-JSON bodies are stored as a JSON string.
     */
    Optional<JsonElement> responseJson() {
        JsonElement jsonBody = testCase.getResponse().getJsonBody();
        if (jsonBody != null && (jsonBody.isJsonObject() || jsonBody.isJsonArray())) {
            return Optional.of(jsonBody);
        }
        String raw = rawResponseBody();
        return StringUtils.isNotBlank(raw) && JsonUtils.isValidJson(raw) && !raw.trim().startsWith("\"")
                ? Optional.of(JsonUtils.parseAsJsonElement(raw))
                : Optional.empty();
    }

    /**
     * Returns the raw response text, unwrapping bodies that reports stored as a JSON string primitive.
     */
    String rawResponseBody() {
        String raw = testCase.getResponse().getBody();
        JsonElement jsonBody = testCase.getResponse().getJsonBody();
        if (StringUtils.isNotBlank(raw) || jsonBody == null || jsonBody.isJsonNull()) {
            return raw;
        }
        return jsonBody.isJsonPrimitive() ? jsonBody.getAsString() : jsonBody.toString();
    }
}
