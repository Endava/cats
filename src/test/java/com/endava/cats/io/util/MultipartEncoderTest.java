package com.endava.cats.io.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@QuarkusTest
class MultipartEncoderTest {
    private static final Path ALL_BYTES = Path.of("src/test/resources/multipart/all-bytes.bin");
    private static final String FILE_REFERENCE = "cats_file:src/test/resources/multipart/all-bytes.bin";

    @Test
    void shouldEncodeFileReferenceAsBinaryPartAndOtherFieldsAsTextParts() throws IOException {
        byte[] expected = Files.readAllBytes(ALL_BYTES);
        String payload = "{\"file\":\"" + FILE_REFERENCE + "\",\"evidenceType\":\"PROOF_OF_DELIVERY\"}";

        MultipartEncoder.Result result = MultipartEncoder.encode(payload, true, Map.of(), _ -> false);

        List<ParsedPart> parts = parse(result);
        Assertions.assertThat(parts).hasSize(2);
        ParsedPart file = part(parts, "file");
        Assertions.assertThat(file.headers()).contains("name=\"file\"; filename=\"all-bytes.bin\"");
        Assertions.assertThat(file.content()).isEqualTo(expected);
        Assertions.assertThat(indexOf(result.body(), expected)).isNotNegative();
        ParsedPart evidenceType = part(parts, "evidenceType");
        Assertions.assertThat(evidenceType.headers()).doesNotContain("filename");
        Assertions.assertThat(new String(evidenceType.content(), StandardCharsets.UTF_8)).isEqualTo("PROOF_OF_DELIVERY");
    }

    @Test
    void shouldResolveAbsoluteFileReferences(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "hello");
        String payload = "{\"attachment\":\"cats_file:" + file.toAbsolutePath().toString().replace("\\", "\\\\") + "\"}";

        MultipartEncoder.Result result = MultipartEncoder.encode(payload, true, Map.of(), _ -> false);

        ParsedPart attachment = part(parse(result), "attachment");
        Assertions.assertThat(attachment.headers()).contains("filename=\"sample.txt\"");
        Assertions.assertThat(attachment.content()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldFailWhenReferencedFileDoesNotExist() {
        String payload = "{\"file\":\"cats_file:does/not/exist.bin\"}";

        Assertions.assertThatThrownBy(() -> MultipartEncoder.encode(payload, true, Map.of(), _ -> false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does/not/exist.bin");
    }

    @Test
    void shouldDecodeBase64ValuesOfBinarySchemasWhenFieldIsNotFuzzed() {
        String payload = "{\"file\":\"aGVsbG8=\"}";

        MultipartEncoder.Result result = MultipartEncoder.encode(payload, true,
                Map.of("file", new BinarySchema()), _ -> false);

        ParsedPart file = part(parse(result), "file");
        Assertions.assertThat(file.headers()).contains("filename=\"file.bin\"").contains("Content-Type: application/octet-stream");
        Assertions.assertThat(file.content()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldSendFuzzedBinaryValuesVerbatim() {
        String payload = "{\"file\":\"aGVsbG8=\"}";

        MultipartEncoder.Result result = MultipartEncoder.encode(payload, true,
                Map.of("file", new StringSchema().format("binary")), "file"::equals);

        ParsedPart file = part(parse(result), "file");
        Assertions.assertThat(file.headers()).contains("filename=\"file.bin\"");
        Assertions.assertThat(file.content()).isEqualTo("aGVsbG8=".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldSendNonBase64BinaryValuesAsUtf8() {
        MultipartEncoder.Result result = MultipartEncoder.encode("{\"file\":\"not base64!\"}", true,
                Map.of("file", new BinarySchema()), _ -> false);

        Assertions.assertThat(part(parse(result), "file").content()).isEqualTo("not base64!".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldFlattenNestedObjectsAndArrays() {
        MultipartEncoder.Result result = MultipartEncoder.encode("{\"meta\":{\"a\":1},\"tags\":[\"x\"]}", true,
                Map.of(), _ -> false);

        List<ParsedPart> parts = parse(result);
        Assertions.assertThat(new String(part(parts, "meta[a]").content(), StandardCharsets.UTF_8)).isEqualTo("1");
        Assertions.assertThat(new String(part(parts, "tags[0]").content(), StandardCharsets.UTF_8)).isEqualTo("x");
    }

    @Test
    void shouldOmitPartsForNullValues() {
        MultipartEncoder.Result result = MultipartEncoder.encode("{\"keep\":\"v\",\"drop\":null,\"nested\":{\"gone\":null}}",
                true, Map.of(), _ -> false);

        List<ParsedPart> parts = parse(result);
        Assertions.assertThat(parts).extracting(ParsedPart::name).containsExactly("keep");
    }

    @Test
    void shouldKeepPayloadOrder() {
        MultipartEncoder.Result result = MultipartEncoder.encode("{\"b\":\"1\",\"a\":\"2\",\"c\":\"3\"}", true,
                Map.of(), _ -> false);

        Assertions.assertThat(parse(result)).extracting(ParsedPart::name).containsExactly("b", "a", "c");
    }

    @Test
    void shouldSendPayloadAsIsWhenItCannotBeEncodedAsParts() {
        for (String payload : new String[]{"not json {", "", "   ", "[{\"a\":1}]", "{}"}) {
            boolean validJson = !payload.startsWith("not json");
            MultipartEncoder.Result result = MultipartEncoder.encode(payload, validJson, Map.of(), _ -> false);

            Assertions.assertThat(result.body()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));
            Assertions.assertThat(result.contentType()).startsWith("multipart/form-data; boundary=cats");
            Assertions.assertThat(result.summary()).isEqualTo(payload);
        }
    }

    @Test
    void shouldSendNullPayloadAsEmptyBody() {
        MultipartEncoder.Result result = MultipartEncoder.encode(null, true, Map.of(), _ -> false);

        Assertions.assertThat(result.body()).isEmpty();
        Assertions.assertThat(result.summary()).isEmpty();
        Assertions.assertThat(result.contentType()).startsWith("multipart/form-data; boundary=cats");
    }

    @Test
    void shouldDescribeFilePartsInSummaryWithoutRawBytes() throws Exception {
        byte[] expected = Files.readAllBytes(ALL_BYTES);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
        String payload = "{\"file\":\"" + FILE_REFERENCE + "\",\"evidenceType\":\"PROOF_OF_DELIVERY\"}";

        MultipartEncoder.Result result = MultipartEncoder.encode(payload, true, Map.of(), _ -> false);

        Assertions.assertThat(result.summary())
                .contains("name=all-bytes.bin", "size=256", "sha256=" + sha256)
                .contains("\"evidenceType\":\"PROOF_OF_DELIVERY\"");
        Assertions.assertThat(result.summary().chars()).allMatch(c -> c >= 0x20 && c < 0x7f);
    }

    @Test
    void shouldUseTheHeaderBoundaryForTheWholeBody() {
        MultipartEncoder.Result result = MultipartEncoder.encode("{\"a\":\"1\",\"b\":\"2\"}", true, Map.of(), _ -> false);

        String boundary = boundaryOf(result);
        String body = new String(result.body(), StandardCharsets.ISO_8859_1);
        Assertions.assertThat(boundary).startsWith("cats");
        Assertions.assertThat(body).startsWith("--" + boundary + "\r\n").endsWith("--" + boundary + "--\r\n");
        Assertions.assertThat(body.split("--" + boundary, -1)).hasSize(4);
    }

    @Test
    void shouldGenerateDifferentBoundariesPerRequest() {
        String first = boundaryOf(MultipartEncoder.encode("{\"a\":\"1\"}", true, Map.of(), _ -> false));
        String second = boundaryOf(MultipartEncoder.encode("{\"a\":\"1\"}", true, Map.of(), _ -> false));

        Assertions.assertThat(first).isNotEqualTo(second);
    }

    private static String boundaryOf(MultipartEncoder.Result result) {
        String contentType = result.contentType();
        return contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());
    }

    private static ParsedPart part(List<ParsedPart> parts, String name) {
        return parts.stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No part named " + name));
    }

    private static List<ParsedPart> parse(MultipartEncoder.Result result) {
        String delimiter = "--" + boundaryOf(result);
        String body = new String(result.body(), StandardCharsets.ISO_8859_1);
        String[] segments = body.split(java.util.regex.Pattern.quote(delimiter), -1);
        List<ParsedPart> parts = new ArrayList<>();
        for (int i = 1; i < segments.length - 1; i++) {
            String segment = segments[i].substring(2);
            int headerEnd = segment.indexOf("\r\n\r\n");
            String headers = segment.substring(0, headerEnd);
            String content = segment.substring(headerEnd + 4, segment.length() - 2);
            int nameStart = headers.indexOf("name=\"") + 6;
            String name = headers.substring(nameStart, headers.indexOf('"', nameStart));
            parts.add(new ParsedPart(name, headers, content.getBytes(StandardCharsets.ISO_8859_1)));
        }
        return parts;
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int i = 0; i <= source.length - target.length; i++) {
            if (Arrays.equals(source, i, i + target.length, target, 0, target.length)) {
                return i;
            }
        }
        return -1;
    }

    private record ParsedPart(String name, String headers, byte[] content) {
    }
}
