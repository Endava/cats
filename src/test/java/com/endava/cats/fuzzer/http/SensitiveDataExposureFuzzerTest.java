package com.endava.cats.fuzzer.http;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class SensitiveDataExposureFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private SensitiveDataExposureFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        fuzzer = new SensitiveDataExposureFuzzer(new SimpleExecutor(testCaseListener, serviceCaller), testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        Mockito.doNothing().when(testCaseListener).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doNothing().when(testCaseListener).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.doNothing().when(testCaseListener).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).contains("writeOnly");
        Assertions.assertThat(fuzzer).hasToString("SensitiveDataExposureFuzzer");
    }

    @Test
    void shouldOnlyRunForMethodsReturningResourceRepresentations() {
        Assertions.assertThat(fuzzer.skipForHttpMethods()).contains(HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE)
                .doesNotContain(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
    }

    @Test
    void shouldReportErrorWhenResponseSchemaWriteOnlyFieldIsExposed() {
        FuzzingData data = dataWithResponseSchema(userSchema());

        fuzzer.checkResponse(response(200, "{\"id\":\"1\",\"name\":\"john\",\"password\":\"s3cr3t\"}"), data);

        verifyError(Set.of("password"));
    }

    @Test
    void shouldReportErrorWhenWriteOnlyFieldIsExposedInsideCollectionsAndReferences() {
        Schema<?> page = new ObjectSchema().addProperty("items", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/User")));
        FuzzingData data = FuzzingData.builder().method(HttpMethod.GET)
                .schemaMap(Map.of("User", userSchema()))
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", page))).build();

        fuzzer.checkResponse(response(200, "{\"items\":[{\"id\":\"1\",\"password\":\"a\"},{\"id\":\"2\",\"password\":\"b\"}]}"), data);

        verifyError(Set.of("items[*].password"));
    }

    @Test
    void shouldReportErrorWhenRequestWriteOnlyFieldIsExposed() {
        Schema<?> requestSchema = new ObjectSchema().addProperty("name", new StringSchema())
                .addProperty("pin", new StringSchema().writeOnly(true));
        FuzzingData data = FuzzingData.builder().method(HttpMethod.POST).reqSchema(requestSchema)
                .requestPropertyTypes(Map.of("name", new StringSchema(), "pin", new StringSchema().writeOnly(true))).build();

        fuzzer.checkResponse(response(201, "{\"name\":\"john\",\"pin\":\"1234\"}"), data);

        verifyError(Set.of("pin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"********\"", "\"xxxx\""})
    void shouldNotReportWriteOnlyFieldsWithoutRealValues(String value) {
        FuzzingData data = dataWithResponseSchema(userSchema());

        fuzzer.checkResponse(response(200, "{\"id\":\"1\",\"password\":" + value + "}"), data);

        verifyPassed();
    }

    @Test
    void shouldReportWarningForUndeclaredSensitiveFields() {
        FuzzingData data = dataWithResponseSchema(new ObjectSchema().addProperty("id", new StringSchema()));

        fuzzer.checkResponse(response(200, "{\"id\":\"1\",\"db_password\":\"p4ss\",\"clientSecret\":\"abc\",\"isSecret\":true}"), data);

        Mockito.verify(testCaseListener).reportResultWarn(Mockito.any(), Mockito.any(),
                Mockito.eq(CatsResultFactory.Reason.SENSITIVE_DATA_EXPOSED.value()), Mockito.anyString(),
                AdditionalMatchers.aryEq(new Object[]{Set.of("db_password", "clientSecret")}));
    }

    @Test
    void shouldNotReportDeclaredSensitiveFieldsThatAreNotWriteOnly() {
        FuzzingData data = dataWithResponseSchema(new ObjectSchema().addProperty("apiKey", new StringSchema()));

        fuzzer.checkResponse(response(200, "{\"apiKey\":\"abc\"}"), data);

        verifyPassed();
    }

    @Test
    void shouldNotReportUndeclaredSensitiveFieldsForFreeFormOrMissingSchemas() {
        fuzzer.checkResponse(response(200, "{\"password\":\"abc\"}"), dataWithResponseSchema(new ObjectSchema()));
        fuzzer.checkResponse(response(200, "{\"password\":\"abc\"}"), FuzzingData.builder().method(HttpMethod.GET).build());

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResult(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
        Mockito.verify(testCaseListener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500})
    void shouldUseStandardReportingForNonSuccessfulResponses(int code) {
        FuzzingData data = dataWithResponseSchema(userSchema());

        fuzzer.checkResponse(response(code, "{\"password\":\"s3cr3t\"}"), data);

        verifyPassed();
    }

    @Test
    void shouldUseStandardReportingForNonJsonResponses() {
        fuzzer.checkResponse(response(200, "password=abc"), dataWithResponseSchema(userSchema()));

        verifyPassed();
    }

    @Test
    void shouldSendHappyPathRequestAndCheckResponse() {
        FuzzingData data = dataWithResponseSchema(userSchema()).toBuilder().payload("{}").path("/users/{id}")
                .contractPath("/users/{id}").headers(Set.of()).reqSchema(new StringSchema())
                .requestContentTypes(List.of("application/json")).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response(200, "{\"id\":\"1\",\"password\":\"s3cr3t\"}"));

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller).call(Mockito.argThat(serviceData -> serviceData.getHttpMethod() == HttpMethod.GET &&
                !serviceData.hasDeclaredMutation()));
        verifyError(Set.of("password"));
    }

    private void verifyError(Set<String> fields) {
        Mockito.verify(testCaseListener).reportResultError(Mockito.any(), Mockito.any(),
                Mockito.eq(CatsResultFactory.Reason.WRITE_ONLY_FIELDS_EXPOSED.value()), Mockito.anyString(),
                AdditionalMatchers.aryEq(new Object[]{fields}));
    }

    private void verifyPassed() {
        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
        Mockito.verify(testCaseListener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
        Mockito.verify(testCaseListener, Mockito.never()).reportResultWarn(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any());
    }

    private static Schema<?> userSchema() {
        return new ObjectSchema().addProperty("id", new StringSchema()).addProperty("name", new StringSchema())
                .addProperty("password", new StringSchema().writeOnly(true));
    }

    private static FuzzingData dataWithResponseSchema(Schema<?> schema) {
        return FuzzingData.builder().method(HttpMethod.GET)
                .responseSchemaDefinitions(Map.of("200", Map.of("application/json", schema))).build();
    }

    private static CatsResponse response(int code, String body) {
        return CatsResponse.builder().responseCode(code).body(body).responseContentType("application/json").build();
    }
}
