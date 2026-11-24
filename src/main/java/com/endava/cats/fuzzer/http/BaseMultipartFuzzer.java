package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.io.util.MultipartMutation;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.PayloadFormat;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Base class for Fuzzers that send structurally invalid {@code multipart/form-data} bodies. The request is encoded
 * as usual, with reference data and file references resolved, and each case then changes the encoded body.
 * It expects a 4XX within the response.
 */
public abstract class BaseMultipartFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final List<String> fuzzedPaths = new ArrayList<>();

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor the executor
     */
    protected BaseMultipartFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    /**
     * Returns the fuzzer logger.
     *
     * @return the logger
     */
    protected PrettyLogger logger() {
        return logger;
    }

    @Override
    public boolean isApplicableTo(FuzzingData data) {
        return data.getPayloadFormat() == PayloadFormat.MULTIPART;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skipping fuzzer as payload is empty");
            return;
        }
        if (!HttpMethod.requiresBody(data.getMethod())) {
            logger.debug("Skipping fuzzer as HTTP method {} has no body", data.getMethod());
            return;
        }
        String runKey = data.getPath() + data.getMethod();
        if (fuzzedPaths.contains(runKey)) {
            logger.skip("Skipping fuzzer as it already fuzzer. Most likely a oneOf/anyOf payload");
            return;
        }
        fuzzedPaths.add(runKey);

        for (MultipartCase multipartCase : cases(data)) {
            simpleExecutor.execute(SimpleExecutorContext.builder()
                    .fuzzingData(data)
                    .logger(logger)
                    .fuzzer(this)
                    .scenario(multipartCase.scenario())
                    .expectedResponseCode(expectedResponseCode(data))
                    .payload(data.getPayload())
                    .multipartMutation(multipartCase.mutation())
                    .mutationTarget(RequestTarget.requestBody())
                    .build());
        }
    }

    /**
     * By default, expect a 4XX response. This can be overridden by subclasses to expect other response codes.
     *
     * @param data the data to fuzz
     * @return the expected response code family
     */
    protected ResponseCodeFamily expectedResponseCode(FuzzingData data) {
        return ResponseCodeFamilyPredefined.FOURXX;
    }

    /**
     * Returns the cases to run, each one producing a separate test.
     *
     * @param data the current FuzzingData
     * @return the cases to run
     */
    protected abstract List<MultipartCase> cases(FuzzingData data);

    /**
     * Returns the top level property names of the payload, in order.
     *
     * @param data the current FuzzingData
     * @return the names, or an empty list if the payload is not a JSON object
     */
    protected static List<String> topLevelPartNames(FuzzingData data) {
        try {
            JsonElement element = JsonUtils.parseAsJsonElement(data.getPayload());
            if (element != null && element.isJsonObject()) {
                return new ArrayList<>(element.getAsJsonObject().keySet());
            }
        } catch (RuntimeException _) {
            return List.of();
        }
        return List.of();
    }

    /**
     * Returns the names of the top level properties declared as binary strings, in order.
     *
     * @param data the current FuzzingData
     * @return the file part names
     */
    protected static List<String> filePartNames(FuzzingData data) {
        if (data.getRequestPropertyTypes() == null) {
            return List.of();
        }
        return topLevelPartNames(data).stream()
                .filter(name -> data.getRequestPropertyTypes().get(name) != null
                        && CatsModelUtils.isBinarySchema(data.getRequestPropertyTypes().get(name)))
                .toList();
    }

    /**
     * Creates a mutation that replaces the first file part with the given name and leaves the body untouched
     * when there is no such part.
     *
     * @param name   the file part name
     * @param change the change applied to the part
     * @return the mutation
     */
    protected static MultipartMutation replaceFilePart(String name, UnaryOperator<MultipartEncoder.Part> change) {
        return encoded -> {
            List<MultipartEncoder.Part> parts = new ArrayList<>(encoded.parts());
            for (int index = 0; index < parts.size(); index++) {
                if (parts.get(index).name().equals(name) && parts.get(index).isFile()) {
                    parts.set(index, change.apply(parts.get(index)));
                    return withBody(encoded, MultipartEncoder.render(encoded.boundary(), parts), encoded.contentType());
                }
            }
            return encoded;
        };
    }

    /**
     * Creates a copy of the encoded body with a different body and content type.
     *
     * @param encoded     the encoded body
     * @param body        the new body bytes
     * @param contentType the new content type
     * @return the new result, with the same summary, boundary and parts
     */
    protected static MultipartEncoder.Result withBody(MultipartEncoder.Result encoded, byte[] body, String contentType) {
        return new MultipartEncoder.Result(body, contentType, encoded.summary(), encoded.boundary(), encoded.parts());
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    /**
     * A test scenario and the change it applies to the encoded body.
     *
     * @param scenario the scenario displayed in the report
     * @param mutation the change applied to the encoded body
     */
    public record MultipartCase(String scenario, MultipartMutation mutation) {
    }
}
