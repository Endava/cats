package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.io.util.MultipartMutation;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Fuzzer that sends multipart requests in which a part has no Content-Disposition header or no name.
 */
@Singleton
@HttpFuzzer
public class MultipartMissingPartHeadersFuzzer extends BaseMultipartFuzzer {

    /**
     * Creates a new MultipartMissingPartHeadersFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public MultipartMissingPartHeadersFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected List<MultipartCase> cases(FuzzingData data) {
        List<MultipartCase> cases = new ArrayList<>();
        for (String name : topLevelPartNames(data)) {
            cases.add(new MultipartCase("Send a multipart request where the [" + name
                    + "] part has no Content-Disposition header", overrideHeaders(name, _ -> "")));
            cases.add(new MultipartCase("Send a multipart request where the [" + name
                    + "] part has a Content-Disposition header without a name",
                    overrideHeaders(name, MultipartMissingPartHeadersFuzzer::dispositionWithoutName)));
        }
        return cases;
    }

    private static String dispositionWithoutName(MultipartEncoder.Part part) {
        return "Content-Disposition: form-data\r\n"
                + (part.isFile() ? "Content-Type: " + part.contentType() + "\r\n" : "");
    }

    private static MultipartMutation overrideHeaders(String name, Function<MultipartEncoder.Part, String> headers) {
        return encoded -> {
            for (int index = 0; index < encoded.parts().size(); index++) {
                MultipartEncoder.Part part = encoded.parts().get(index);
                if (part.name().equals(name)) {
                    byte[] body = MultipartEncoder.render(encoded.boundary(), encoded.parts(),
                            Map.of(index, headers.apply(part)));
                    return withBody(encoded, body, encoded.contentType());
                }
            }
            return encoded;
        };
    }

    @Override
    public String description() {
        return "send multipart requests where a part has no Content-Disposition header or no name";
    }
}
