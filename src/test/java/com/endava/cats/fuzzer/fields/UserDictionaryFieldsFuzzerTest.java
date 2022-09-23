package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.executor.CatsExecutor;
import com.endava.cats.model.FuzzingData;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.List;

@QuarkusTest
class UserDictionaryFieldsFuzzerTest {

    private CatsExecutor catsExecutor;
    private UserArguments userArguments;
    private MatchArguments matchArguments;
    private UserDictionaryFieldsFuzzer userDictionaryFieldsFuzzer;

    private PrettyLogger prettyLogger;

    @BeforeEach
    void setup() {
        catsExecutor = Mockito.mock(CatsExecutor.class);
        userArguments = Mockito.mock(UserArguments.class);
        matchArguments = Mockito.mock(MatchArguments.class);
        prettyLogger = Mockito.mock(PrettyLogger.class);

        userDictionaryFieldsFuzzer = new UserDictionaryFieldsFuzzer(catsExecutor, userArguments, matchArguments);
        ReflectionTestUtils.setField(userDictionaryFieldsFuzzer, "logger", prettyLogger);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(userDictionaryFieldsFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(userDictionaryFieldsFuzzer).hasToString(userDictionaryFieldsFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldNotRunWhenWordListIsNull() {
        userDictionaryFieldsFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verify(prettyLogger, Mockito.times(1)).error("Skipping fuzzer as --words was not provided!");
        Mockito.verifyNoInteractions(catsExecutor);
    }

    @Test
    void shouldNotRunWhenNoMatchArgumentsSupplied() {
        Mockito.when(userArguments.getWords()).thenReturn(new File("test"));
        userDictionaryFieldsFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verify(prettyLogger, Mockito.times(1)).error("Skipping fuzzer as no --m* argument was provided!");
        Mockito.verifyNoInteractions(catsExecutor);
    }

    @Test
    void shouldRunWhenWordsNotNullAndMatchArgSupplied() {
        Mockito.when(userArguments.getWords()).thenReturn(new File("test"));
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);

        userDictionaryFieldsFuzzer.fuzz(Mockito.mock(FuzzingData.class));
        Mockito.verify(catsExecutor, Mockito.times(1)).execute(Mockito.any());
    }
}
