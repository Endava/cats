package com.endava.cats.report;

import com.endava.cats.model.RequestVocabulary;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@QuarkusTest
class ErrorTemplateMinerTest {
    private static final List<String> FIXTURE = List.of(
            "Cluster cluster-1 cannot be found.",
            "Cannot invoke \"String.getBytes()\" because \"extraData\" is null",
            "Cluster cluster-1& cannot be found.",
            "Cannot invoke \"String.length()\" because \"val\" is null",
            "Cluster cluster-1\u200b cannot be found.",
            "Cannot invoke \"java.math.BigInteger.bitLength()\" because \"value\" is null",
            "Cluster cluster-1 could not be found.",
            "Topic 'orders' already exists.");

    @Test
    void shouldGeneralizeEchoedValuesIntoOneTemplate() {
        List<ErrorTemplate> templates = mine(500, "Cluster c1 cannot be found.", "Cluster c2 cannot be found.");

        Assertions.assertThat(templates).extracting(ErrorTemplate::text).containsOnly("Cluster <*> cannot be found.");
        Assertions.assertThat(templates.getFirst().id()).isEqualTo(templates.getLast().id()).hasSize(8);
    }

    @Test
    void shouldKeepDistinctCrashSitesApart() {
        List<ErrorTemplate> templates = mine(500, FIXTURE.get(1), FIXTURE.get(3), FIXTURE.get(5));

        Assertions.assertThat(templates).extracting(ErrorTemplate::id).doesNotHaveDuplicates();
    }

    @Test
    void shouldNotKeepGeneralizingByChaining() {
        List<ErrorTemplate> templates = mine(400, "a b c d e", "a X c d e", "a X c Y e");

        Assertions.assertThat(templates.get(0).id()).isEqualTo(templates.get(1).id());
        Assertions.assertThat(templates.get(2).id()).isNotEqualTo(templates.get(0).id());
    }

    static Stream<Arguments> differentPartitions() {
        return Stream.of(
                Arguments.of(sample("", 500, "{\"title\":\"TypeA\",\"detail\":\"failed\"}"), sample("", 500, "{\"title\":\"TypeB\",\"detail\":\"failed\"}")),
                Arguments.of(sample("", 500, "failed hard"), sample("", 502, "failed hard")));
    }

    @ParameterizedTest
    @MethodSource("differentPartitions")
    void shouldNeverMergeAcrossPartitions(ErrorSample first, ErrorSample second) {
        List<ErrorTemplate> templates = ErrorTemplateMiner.mine(List.of(first, second));

        Assertions.assertThat(templates.getFirst()).isNotEqualTo(templates.getLast());
    }

    @Test
    void shouldLearnTemplatesSeparatelyPerScope() {
        List<ErrorTemplate> templates = ErrorTemplateMiner.mine(List.of(
                sample("Reason A", 500, "user u1 is locked"), sample("Reason A", 500, "user u2 is locked"),
                sample("Reason B", 500, "user u1 is locked")));

        Assertions.assertThat(templates).extracting(ErrorTemplate::text)
                .containsExactly("user <*> is locked", "user <*> is locked", "user u1 is locked");
    }

    @Test
    void shouldGroupTheSameWayRegardlessOfObservationOrder() {
        Map<String, Integer> reference = groupIndexByMessage(FIXTURE);

        Assertions.assertThat(groupIndexByMessage(FIXTURE.reversed())).isEqualTo(reference);
        Assertions.assertThat(groupIndexByMessage(IntStream.range(0, FIXTURE.size())
                .mapToObj(index -> FIXTURE.get((index * 3) % FIXTURE.size())).toList())).isEqualTo(reference);
    }

    @Test
    void shouldProduceStableIdsAcrossRuns() {
        Assertions.assertThat(mine(500, FIXTURE.toArray(String[]::new))).isEqualTo(mine(500, FIXTURE.toArray(String[]::new)));
    }

    @Test
    void shouldRenderTypeStackFrameAndEmptyBodies() {
        ErrorSample withFrame = sample("", 500, "{\"title\":\"NullPointerException\",\"detail\":\"boom\",\"trace\":\"at com.acme.Svc.run(Svc.java:1)\"}");
        ErrorSample empty = sample("", 404, "");

        Assertions.assertThat(ErrorTemplateMiner.mine(List.of(withFrame, empty))).extracting(ErrorTemplate::text)
                .containsExactly("NullPointerException: boom @ com.acme.Svc.run(Svc.java:1)", "[Empty response body]");
    }

    /**
     * Maps each message to a canonical group number (the index of the first message sharing its template), so two
     * groupings can be compared independently of the order the messages were mined in.
     */
    private static Map<String, Integer> groupIndexByMessage(List<String> messages) {
        List<ErrorTemplate> templates = mine(500, messages.toArray(String[]::new));
        Map<String, String> idByMessage = IntStream.range(0, messages.size()).boxed()
                .collect(Collectors.toMap(messages::get, index -> templates.get(index).id()));
        return FIXTURE.stream().collect(Collectors.toMap(Function.identity(),
                message -> FIXTURE.stream().map(idByMessage::get).toList().indexOf(idByMessage.get(message))));
    }

    private static List<ErrorTemplate> mine(int status, String... messages) {
        return ErrorTemplateMiner.mine(Stream.of(messages).map(message -> sample("", status, message)).toList());
    }

    private static ErrorSample sample(String scope, int status, String body) {
        return new ErrorSample(scope, status, ErrorSignatureMasker.signatureOf(body, null, RequestVocabulary.EMPTY));
    }
}
