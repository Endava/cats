package com.endava.cats.fuzzer.executor;

import com.endava.cats.Fuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FuzzingData;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import lombok.Builder;
import lombok.Value;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@Builder
@Value
public class CatsHttpExecutorContext {

    PrettyLogger logger;
    FuzzingData fuzzingData;
    String scenario;
    /**
     * If you provide an expected response code, it will be used and matched against what the service returns and report info, warn or error accordingly.
     * If not supplied, CatsFieldsExecutor will test for Marching Arguments. If any match is found it will be reported as error, otherwise the test will be marked as skipped.
     */
    ResponseCodeFamily expectedResponseCode;
    Fuzzer fuzzer;
    @Builder.Default
    boolean replaceRefData = true;
    @Builder.Default
    Predicate<HttpMethod> runFilter = httpMethod -> true;
    @Builder.Default
    Set<String> skippedHeaders = new HashSet<>();
    String payload;
}
