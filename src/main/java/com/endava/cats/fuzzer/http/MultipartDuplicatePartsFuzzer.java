package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Fuzzer that sends multipart requests in which a part is duplicated, one part at a time.
 */
@Singleton
@HttpFuzzer
public class MultipartDuplicatePartsFuzzer extends BaseMultipartFuzzer {

    /**
     * Creates a new MultipartDuplicatePartsFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public MultipartDuplicatePartsFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected List<MultipartCase> cases(FuzzingData data) {
        return topLevelPartNames(data).stream()
                .map(name -> new MultipartCase("Send a multipart request with the [" + name + "] part duplicated",
                        encoded -> withBody(encoded,
                                MultipartEncoder.render(encoded.boundary(), duplicate(encoded.parts(), name)),
                                encoded.contentType())))
                .toList();
    }

    private static List<MultipartEncoder.Part> duplicate(List<MultipartEncoder.Part> parts, String name) {
        List<MultipartEncoder.Part> result = new ArrayList<>();
        for (MultipartEncoder.Part part : parts) {
            result.add(part);
            if (part.name().equals(name)) {
                result.add(part);
            }
        }
        return result;
    }

    @Override
    public String description() {
        return "send a multipart request with each part duplicated, one part at a time";
    }
}
