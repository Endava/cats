package com.endava.cats.io;

import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class BoundedResponseBodyReader {
    private BoundedResponseBodyReader() {
    }

    public static CapturedBody read(Response response, long maxResponseBytes) throws IOException {
        long declaredContentLength = response.body().contentLength();
        if (declaredContentLength < 0) {
            try {
                declaredContentLength = Long.parseLong(Optional.ofNullable(response.header("Content-Length")).orElse("-1"));
            } catch (NumberFormatException _) {
                declaredContentLength = -1;
            }
        }
        Charset charset = Optional.ofNullable(response.body().contentType())
                .map(contentType -> contentType.charset(StandardCharsets.UTF_8))
                .orElse(StandardCharsets.UTF_8);
        if (maxResponseBytes == 0 || maxResponseBytes == Long.MAX_VALUE) {
            String body = response.body().string();
            return new CapturedBody(body, body.getBytes(charset).length, declaredContentLength, false);
        }

        BufferedSource source = response.body().source();
        Buffer buffer = new Buffer();
        long captureLimit = maxResponseBytes + 1;
        while (buffer.size() < captureLimit) {
            long read = source.read(buffer, Math.min(8192, captureLimit - buffer.size()));
            if (read == -1) {
                break;
            }
        }
        boolean truncated = buffer.size() > maxResponseBytes;
        byte[] bytes = buffer.readByteArray(Math.min(buffer.size(), maxResponseBytes));
        return new CapturedBody(new String(bytes, charset), bytes.length, declaredContentLength, truncated);
    }

    public record CapturedBody(String body, long capturedBytes, long declaredContentLength, boolean truncated) {
    }
}
