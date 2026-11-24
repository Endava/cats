package com.endava.cats.fuzzer.http;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.factory.FuzzingDataFactory;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.BidirectionalOverrideFieldsFuzzer;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.NoMediaType;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

@QuarkusTest
class MultipartWholeBodyFuzzersTest {
    private static final String PATH = "/cases/{caseId}/evidence/uploads";

    @Inject
    CatsGlobalContext catsGlobalContext;
    @Inject
    ValidDataFormat validDataFormat;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ServiceCaller serviceCaller;
    private SimpleExecutor simpleExecutor;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    private static Stream<Arguments> genericWholeBodyFuzzers() {
        return Stream.of(
                fuzzer("EmptyBody", EmptyBodyFuzzer::new),
                fuzzer("NullBody", NullBodyFuzzer::new),
                fuzzer("RandomStringBody", RandomStringBodyFuzzer::new),
                fuzzer("NullUnicodeBody", NullUnicodeBodyFuzzer::new),
                fuzzer("NullUnicodeSymbolBody", NullUnicodeSymbolBodyFuzzer::new),
                fuzzer("DummyRequest", DummyRequestFuzzer::new),
                fuzzer("RandomNegativeDecimalBody", RandomNegativeDecimalBodyFuzzer::new),
                fuzzer("RandomNegativeIntegerBody", RandomNegativeIntegerBodyFuzzer::new),
                fuzzer("RandomPositiveDecimalBody", RandomPositiveDecimalBodyFuzzer::new),
                fuzzer("RandomPositiveIntegerBody", RandomPositiveIntegerBodyFuzzer::new),
                fuzzer("RandomUnicodeBody", RandomUnicodeBodyFuzzer::new),
                fuzzer("ZeroIntegerBody", ZeroIntegerBodyFuzzer::new),
                fuzzer("ZeroDecimalBody", ZeroDecimalBodyFuzzer::new));
    }

    private static Stream<Arguments> jsonOnlyFuzzers() {
        return Stream.of(
                fuzzer("MalformedJson", MalformedJsonFuzzer::new),
                fuzzer("RandomDummyInvalidJsonBody", RandomDummyInvalidJsonBodyFuzzer::new),
                fuzzer("EmptyJsonArrayBody", EmptyJsonArrayBodyFuzzer::new));
    }

    private static Stream<Arguments> rawBodyFuzzers() {
        return genericWholeBodyFuzzers().filter(arguments -> List.of("EmptyBody", "NullBody", "RandomStringBody",
                "NullUnicodeBody", "DummyRequest").contains(arguments.get()[0]));
    }

    private static Arguments fuzzer(String name, Function<SimpleExecutor, Fuzzer> constructor) {
        return Arguments.of(name, constructor);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("genericWholeBodyFuzzers")
    void shouldApplyGenericWholeBodyFuzzersToMultipart(String name, Function<SimpleExecutor, Fuzzer> constructor) {
        Fuzzer fuzzer = constructor.apply(simpleExecutor);

        Assertions.assertThat(fuzzer.isApplicableTo(MultipartFuzzerTestSupport.multipartData(HttpMethod.POST,
                MultipartFuzzerTestSupport.PAYLOAD))).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonOnlyFuzzers")
    void shouldNotApplyJsonOnlyFuzzersToMultipart(String name, Function<SimpleExecutor, Fuzzer> constructor) {
        Fuzzer fuzzer = constructor.apply(simpleExecutor);

        Assertions.assertThat(fuzzer.isApplicableTo(MultipartFuzzerTestSupport.multipartData(HttpMethod.POST,
                MultipartFuzzerTestSupport.PAYLOAD))).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rawBodyFuzzers")
    void shouldSendWholeBodyFuzzerPayloadAsRawMultipartRequest(String name, Function<SimpleExecutor, Fuzzer> constructor)
            throws IOException {
        FuzzingData data = multipartOperation();
        Fuzzer fuzzer = constructor.apply(simpleExecutor);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        ArgumentCaptor<ResponseCodeFamily> family = ArgumentCaptor.forClass(ResponseCodeFamily.class);
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                family.capture(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> serviceData = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller).call(serviceData.capture());
        ServiceData sent = serviceData.getValue();
        Assertions.assertThat(sent.getContentType()).isEqualTo("multipart/form-data");
        Assertions.assertThat(sent.isValidJson()).isFalse();
        Assertions.assertThat(sent.isReplaceRefData()).isFalse();
        MultipartEncoder.Result encoded = MultipartEncoder.encode(sent.getPayload(), sent.isValidJson(),
                sent.getRequestPropertyTypes(), _ -> false);
        Assertions.assertThat(encoded.parts()).isEmpty();
        Assertions.assertThat(encoded.body()).isEqualTo(sent.getPayload().getBytes(StandardCharsets.UTF_8));
        Assertions.assertThat(encoded.contentType()).startsWith("multipart/form-data; boundary=cats");
        Assertions.assertThat(family.getValue().asString()).startsWith("4");
    }

    @Test
    void shouldApplyFieldFuzzersToMultipartTextParts() throws IOException {
        FuzzingData data = multipartOperation();
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, testCaseListener,
                Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        BidirectionalOverrideFieldsFuzzer fieldFuzzer = new BidirectionalOverrideFieldsFuzzer(executor, processingArguments);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.empty());
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.eq(data), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean());

        fieldFuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> serviceData = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(serviceData.capture());
        ServiceData checksumCall = serviceData.getAllValues().stream()
                .filter(call -> JsonUtils.getVariableFromJson(call.getPayload(), "checksum").toString().contains("\u202E"))
                .findFirst().orElseThrow();
        String fuzzedChecksum = String.valueOf(JsonUtils.getVariableFromJson(checksumCall.getPayload(), "checksum"));
        MultipartEncoder.Result encoded = MultipartEncoder.encode(checksumCall.getPayload(), checksumCall.isValidJson(),
                checksumCall.getRequestPropertyTypes(), _ -> false);
        Assertions.assertThat(encoded.parts()).filteredOn(part -> "checksum".equals(part.name()))
                .singleElement().satisfies(part -> {
                    Assertions.assertThat(part.isFile()).isFalse();
                    Assertions.assertThat(new String(part.bytes(), StandardCharsets.UTF_8)).isEqualTo(fuzzedChecksum);
                });
        Assertions.assertThat(encoded.parts()).extracting(MultipartEncoder.Part::name)
                .contains("file", "evidenceType", "checksum");
    }

    private FuzzingData multipartOperation() throws IOException {
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        ProcessingArguments processingArguments = Mockito.mock(ProcessingArguments.class);
        FilterArguments filterArguments = Mockito.mock(FilterArguments.class);
        Mockito.when(processingArguments.getUseExamples()).thenReturn(true);
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(10);
        Mockito.when(processingArguments.getContentType()).thenReturn(List.of(JsonUtils.JSON_WILDCARD, "application/x-www-form-urlencoded"));
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(true, true, false, true));
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(4);
        Mockito.when(filesArguments.isNotUrlParam(Mockito.anyString())).thenReturn(true);
        FuzzingDataFactory factory = new FuzzingDataFactory(filesArguments, processingArguments, catsGlobalContext,
                validDataFormat, filterArguments);

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = new OpenAPIParser().readContents(
                Files.readString(Paths.get("src/test/resources/multipart-upload.yml")), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application\\/.*\\+?json"));
        catsGlobalContext.getSchemaMap().clear();
        catsGlobalContext.getSchemaMap().putAll(schemas);
        catsGlobalContext.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        catsGlobalContext.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        catsGlobalContext.getSchemaMap().remove("");
        catsGlobalContext.setOpenAPI(openAPI);

        return factory.fromPathItem(PATH, openAPI.getPaths().get(PATH), openAPI).stream()
                .filter(data -> data.getMethod() == HttpMethod.POST).findFirst().orElseThrow();
    }
}
