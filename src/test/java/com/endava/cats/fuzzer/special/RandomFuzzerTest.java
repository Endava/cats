package com.endava.cats.fuzzer.special;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class RandomFuzzerTest {
    private SimpleExecutor simpleExecutor;
    private TestCaseListener testCaseListener;
    private ExecutionStatisticsListener executionStatisticsListener;
    private MatchArguments matchArguments;
    private StopArguments stopArguments;
    private RandomFuzzer randomFuzzer;
    private FilesArguments filesArguments;
    @Inject
    Instance<Mutator> mutators;

    @BeforeEach
    void setup() {
        simpleExecutor = Mockito.mock(SimpleExecutor.class);
        stopArguments = Mockito.mock(StopArguments.class);
        executionStatisticsListener = Mockito.mock(ExecutionStatisticsListener.class);
        matchArguments = Mockito.mock(MatchArguments.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        CatsRandom.initRandom(0);

        randomFuzzer = new RandomFuzzer(simpleExecutor, testCaseListener,
                executionStatisticsListener,
                matchArguments, mutators,
                stopArguments, filesArguments);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldNotRunWhenEmptyPayload() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        randomFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunWhenPayloadNotEmpty() {
        Instance<Mutator> bodyMutators = Mockito.mock(Instance.class);
        Mockito.when(bodyMutators.stream()).thenReturn(Stream.of(new BodyMutator() {
            @Override
            public String mutate(String inputJson, String selectedField) {
                return inputJson.replace("value", "mutated");
            }

            @Override
            public String description() {
                return "test body mutation";
            }
        }));
        randomFuzzer = new RandomFuzzer(simpleExecutor, testCaseListener, executionStatisticsListener,
                matchArguments, bodyMutators, stopArguments, filesArguments);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"id\":\"value\"}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);
        Mockito.when(data.getPath()).thenReturn("/resources?id=value");
        Mockito.when(data.getQueryParams()).thenReturn(Set.of("id"));
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("id"));
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong())).thenReturn(true);

        randomFuzzer.fuzz(data);

        ArgumentCaptor<SimpleExecutorContext> context = ArgumentCaptor.forClass(SimpleExecutorContext.class);
        Mockito.verify(simpleExecutor).execute(context.capture());
        Assertions.assertThat(context.getValue().getMutationTargets()).containsExactly(RequestTarget.query("id"));
    }

    @Test
    void shouldRunForMultipleTimes() {
        FuzzingData data = mockData();
        randomFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
    }

    @Test
    void shouldReportError() {
        CatsResponse response = CatsResponse.empty();
        FuzzingData data = FuzzingData.builder().build();
        Mockito.when(matchArguments.isMatchResponse(response)).thenReturn(true);
        Mockito.when(matchArguments.getMatchString()).thenReturn(" test");

        randomFuzzer.processResponse(response, data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultError(Mockito.any(), Mockito.eq(data),
                        Mockito.eq("Response matches arguments"), Mockito.eq("Response matches test"));
    }

    @Test
    void shouldReportSkip() {
        CatsResponse response = CatsResponse.empty();
        FuzzingData data = FuzzingData.builder().build();

        randomFuzzer.processResponse(response, data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .skipTest(Mockito.any(), Mockito.eq("Skipping test as response does not match given matchers!"));
    }

    @Test
    void shouldOverrideMethods() {
        Assertions.assertThat(randomFuzzer.description()).isNotBlank();
        Assertions.assertThat(randomFuzzer).hasToString("RandomFuzzer");
    }

    @Test
    void shouldNotRunAnyTestWhenEmptyMutators() {
        Mockito.when(filesArguments.getMutatorsFolder()).thenReturn(new File("src/tests"));
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"id\":\"value\"}");
        randomFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(simpleExecutor);
    }

    @Test
    void shouldRunCustomMutatorsFromValidFolder() throws Exception {
        Mockito.when(filesArguments.getMutatorsFolder()).thenReturn(new File("src/test/resources/mutators"));
        FuzzingData data = mockData();
        RandomFuzzer randomFuzzerSpy = Mockito.spy(randomFuzzer);
        randomFuzzerSpy.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(3)).execute(Mockito.any());
        Mockito.verify(randomFuzzerSpy, Mockito.times(3)).createConfig(Mockito.any());
        Mockito.verify(randomFuzzerSpy, Mockito.times(1)).readValueFromFile(Mockito.endsWith("dict.txt"));
    }

    @Test
    void shouldApplyNewHighImpactMutatorsAndMultiFieldMutations() {
        com.endava.cats.fuzzer.special.mutators.impl.RandomBoundaryNumbersMutator boundaryMutator =
                new com.endava.cats.fuzzer.special.mutators.impl.RandomBoundaryNumbersMutator();
        com.endava.cats.fuzzer.special.mutators.impl.RandomTypeConfusionMutator typeConfusionMutator =
                new com.endava.cats.fuzzer.special.mutators.impl.RandomTypeConfusionMutator();
        com.endava.cats.fuzzer.special.mutators.impl.RandomInjectionAndFormatCrashMutator injectionMutator =
                new com.endava.cats.fuzzer.special.mutators.impl.RandomInjectionAndFormatCrashMutator();

        String baseJson = "{\"a\":\"one\",\"b\":2,\"c\":\"three\"}";
        Assertions.assertThat(boundaryMutator.mutate(baseJson, "b")).isNotEqualTo(baseJson);
        Assertions.assertThat(boundaryMutator.description()).isNotBlank();
        Assertions.assertThat(typeConfusionMutator.mutate(baseJson, "a")).isNotEqualTo(baseJson);
        Assertions.assertThat(typeConfusionMutator.description()).isNotBlank();
        Assertions.assertThat(injectionMutator.mutate(baseJson, "c")).isNotEqualTo(baseJson);
        Assertions.assertThat(injectionMutator.description()).isNotBlank();

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn(baseJson);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/path");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("a", "b", "c"));
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(false).thenReturn(true);

        randomFuzzer.fuzz(data);
        Mockito.verify(simpleExecutor, Mockito.times(2)).execute(Mockito.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleCapPerOperationMissingFieldsTruncatedResponseAndMutatorHeaders() throws Exception {
        CatsResponse truncated = CatsResponse.empty();
        FuzzingData emptyData = FuzzingData.builder().build();
        Mockito.when(testCaseListener.reportTruncatedResponse(Mockito.any(), Mockito.eq(emptyData), Mockito.eq(truncated)))
                .thenReturn(true);
        randomFuzzer.processResponse(truncated, emptyData);
        Mockito.verify(testCaseListener, Mockito.never()).skipTest(Mockito.any(), Mockito.anyString());

        Instance<Mutator> headerAndBodyMutator = Mockito.mock(Instance.class);
        Mockito.when(headerAndBodyMutator.stream()).thenAnswer(_ -> Stream.of(new Mutator() {
            @Override
            public String mutate(String inputJson, String selectedField) {
                return inputJson;
            }

            @Override
            public java.util.Collection<com.endava.cats.model.CatsHeader> mutate(java.util.Collection<com.endava.cats.model.CatsHeader> headers) {
                return java.util.List.of(
                        com.endava.cats.model.CatsHeader.from("X-Same", "1", false, null),
                        com.endava.cats.model.CatsHeader.from("X-Mutated", "2", false, null));
            }

            @Override
            public String description() {
                return "header mutator";
            }
        }));
        Mockito.when(stopArguments.getStopAfterTimeInSec()).thenReturn(60L);
        Mockito.when(stopArguments.getStopAfterMutations()).thenReturn(0L);
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong())).thenReturn(false);

        RandomFuzzer cappedFuzzer = new RandomFuzzer(simpleExecutor, testCaseListener, executionStatisticsListener,
                matchArguments, headerAndBodyMutator, stopArguments, filesArguments);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"id\":\"value\"}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/path");
        Mockito.when(data.getHeaders()).thenReturn(Set.of(com.endava.cats.model.CatsHeader.from("X-Same", "1", false, null)));
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("missingField", "id"));

        cappedFuzzer.fuzzContinuous(data);
        Mockito.verify(simpleExecutor, Mockito.times(25)).execute(Mockito.any());

        Mockito.doAnswer(inv -> {
            com.endava.cats.fuzzer.executor.SimpleExecutorContext ctx = inv.getArgument(0);
            ctx.getResponseProcessor().accept(CatsResponse.builder().responseCode(500).build(), data);
            return null;
        }).when(simpleExecutor).execute(Mockito.any());
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(false).thenReturn(true);
        cappedFuzzer.fuzzContinuous(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResultError(Mockito.any(), Mockito.eq(data),
                Mockito.anyString(), Mockito.contains("500"));

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("bad-mutators");
        java.nio.file.Path badFile = tempDir.resolve("bad.yml");
        java.nio.file.Files.writeString(badFile, "name: test\ntype: INVALID_TYPE\nvalues: [1]\n");
        Mockito.when(filesArguments.getMutatorsFolder()).thenReturn(tempDir.toFile());
        cappedFuzzer.fuzz(data);
        java.nio.file.Files.deleteIfExists(badFile);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @NotNull
    private FuzzingData mockData() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getPayload()).thenReturn("{\"id\":\"value\"}");
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Mockito.when(data.getPath()).thenReturn("/path");
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("id"));
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(false).thenReturn(false).thenReturn(true);
        return data;
    }
}
