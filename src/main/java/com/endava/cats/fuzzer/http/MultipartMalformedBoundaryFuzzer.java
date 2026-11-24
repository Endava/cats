package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Fuzzer that sends multipart requests with a mismatched, unterminated or malformed boundary.
 */
@Singleton
@HttpFuzzer
public class MultipartMalformedBoundaryFuzzer extends BaseMultipartFuzzer {

    /**
     * Creates a new MultipartMalformedBoundaryFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public MultipartMalformedBoundaryFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected List<MultipartCase> cases(FuzzingData data) {
        return List.of(
                new MultipartCase("Send a multipart request whose body boundary differs from the Content-Type boundary",
                        encoded -> withBody(encoded, MultipartEncoder.render(encoded.boundary() + "x", encoded.parts()),
                                MultipartEncoder.contentType(encoded.boundary()))),
                new MultipartCase("Send a multipart request without the closing boundary delimiter",
                        encoded -> withBody(encoded, withoutClosingDelimiter(encoded), encoded.contentType())),
                new MultipartCase("Send a multipart request with a boundary delimiter not followed by CRLF",
                        encoded -> withBody(encoded, withoutCrlfAfterFirstDelimiter(encoded), encoded.contentType())));
    }

    private static byte[] withoutClosingDelimiter(MultipartEncoder.Result encoded) {
        byte[] body = MultipartEncoder.render(encoded.boundary(), encoded.parts());
        int closingLength = ("--" + encoded.boundary() + "--\r\n").getBytes(StandardCharsets.UTF_8).length;
        return Arrays.copyOf(body, body.length - closingLength);
    }

    private static byte[] withoutCrlfAfterFirstDelimiter(MultipartEncoder.Result encoded) {
        byte[] body = MultipartEncoder.render(encoded.boundary(), encoded.parts());
        int delimiterLength = ("--" + encoded.boundary()).getBytes(StandardCharsets.UTF_8).length;
        byte[] result = new byte[body.length - 2];
        System.arraycopy(body, 0, result, 0, delimiterLength);
        System.arraycopy(body, delimiterLength + 2, result, delimiterLength, body.length - delimiterLength - 2);
        return result;
    }

    @Override
    public String description() {
        return "send multipart requests with a mismatched, unterminated or malformed boundary";
    }
}
