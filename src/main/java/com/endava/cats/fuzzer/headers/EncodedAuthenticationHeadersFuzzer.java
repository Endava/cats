package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Exercises Base64 decoder boundaries in Basic and JWT envelopes, without treating acceptance as an auth bypass. */
@Singleton
@HeaderFuzzer
public class EncodedAuthenticationHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor executor;
    private final RobustnessResponseReporter reporter;
    private final AuthArguments auth;
    private final FilesArguments files;
    private final Set<Operation> visited = new HashSet<>();

    public EncodedAuthenticationHeadersFuzzer(SimpleExecutor executor, AuthArguments auth, FilesArguments files, RobustnessResponseReporter reporter) {
        this.executor = executor;
        this.reporter = reporter;
        this.auth = auth;
        this.files = files;
    }

    @Override
    public void fuzz(FuzzingData data) {
        var schemes = EncodedAuthenticationInputs.schemes(data, configuredAuthorization(data), auth.isBasicAuthSupplied());
        if (!schemes.isEmpty() && visited.add(new Operation(data.getMethod(), data.getPath()))) {
            schemes.stream().flatMap(scheme -> EncodedAuthenticationInputs.values(scheme).stream()).forEach(value -> execute(data, value));
        }
    }

    private String configuredAuthorization(FuzzingData data) {
        return files.getHeaders(data.getPath()).entrySet().stream().filter(entry -> "Authorization".equalsIgnoreCase(entry.getKey()))
                .map(entry -> String.valueOf(entry.getValue())).findFirst().orElse("");
    }

    private void execute(FuzzingData data, String value) {
        executor.execute(SimpleExecutorContext.builder().fuzzingData(data).fuzzer(this).logger(logger)
                .scenario("Send a bounded decoder-boundary input in the %s authentication envelope.".formatted(value.startsWith("Basic ") ? "Basic" : "JWT"))
                .headers(headers(data, value)).mutationTarget(RequestTarget.header("Authorization"))
                .responseProcessor((response, context) -> reporter.report(logger, context, response)).expectedResponseCode(RobustnessResponseReporter.expectedResponses()).matchResponseResult(false).matchResponseContentType(false).build());
    }

    private static Collection<CatsHeader> headers(FuzzingData data, String value) {
        Stream<CatsHeader> retained = Optional.ofNullable(data.getHeaders()).orElse(Set.of()).stream()
                .filter(header -> !"Authorization".equalsIgnoreCase(header.getName()));
        return Stream.concat(retained, Stream.of(CatsHeader.from("Authorization", value, true, null))).toList();
    }

    @Override
    public String description() {
        return "checks declared or configured Basic and JWT authentication decoders with bounded Base64 tails";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record Operation(HttpMethod method, String path) {
    }
}
