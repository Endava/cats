package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Builder
@Getter
public class CatsExecutorContext {

    private PrettyLogger logger;
    private FuzzingStrategy fuzzingStrategy;
    private FuzzingData fuzzingData;
    private String scenario;
    private ResponseCodeFamily expectedResponseCode;
    private Fuzzer fuzzer;
    @Builder.Default
    private Predicate<Schema<?>> schemaFilter = schema -> true;
    @Builder.Default
    private Predicate<String> fieldFilter = field -> true;
    private String skipMessage;
    private Function<Schema<?>, List<String>> fuzzValueProducer;
}
