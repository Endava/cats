package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fuzzer that will bypass common authentication headers.
 */
@Singleton
@HttpFuzzer
public class BypassAuthenticationFuzzer implements Fuzzer {
    private static final List<String> AUTH_HEADERS = Arrays.asList("authorization", "authorisation", "token", "jwt", "apikey", "secret", "secretkey", "apisecret", "apitoken", "appkey", "appid");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(BypassAuthenticationFuzzer.class);
    private final FilesArguments filesArguments;
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new BypassAuthenticationFuzzer instance.
     *
     * @param ce             the executor
     * @param filesArguments files arguments
     */
    public BypassAuthenticationFuzzer(SimpleExecutor ce, FilesArguments filesArguments) {
        this.simpleExecutor = ce;
        this.filesArguments = filesArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> authenticationHeaders = this.getAuthenticationHeaderProvided(data);
        if (authenticationHeaders.isEmpty()) {
            logger.skip("No authentication header provided.");
            return;
        }
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .logger(logger)
                        .fuzzingData(data)
                        .payload(data.getPayload())
                        .scenario("Send a happy flow bypassing authentication")
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_AA)
                        .skippedHeaders(authenticationHeaders)
                        .build());
    }

    Set<String> getAuthenticationHeaderProvided(FuzzingData data) {
        Set<String> authenticationHeadersInContract = data.getHeaders().stream().map(CatsHeader::getName)
                .filter(this::isAuthenticationHeader).collect(Collectors.toSet());
        Set<String> authenticationHeadersInFile = filesArguments.getHeaders(data.getPath()).keySet()
                .stream().filter(this::isAuthenticationHeader)
                .collect(Collectors.toSet());

        return Stream.of(authenticationHeadersInContract, authenticationHeadersInFile).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private boolean isAuthenticationHeader(String header) {
        return AUTH_HEADERS.stream().anyMatch(authHeader -> header.toLowerCase().replaceAll("[-_]", "").contains(authHeader));
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "check if an authentication header is supplied; if yes try to make requests without it";
    }
}
