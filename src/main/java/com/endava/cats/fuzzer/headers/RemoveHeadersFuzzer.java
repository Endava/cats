package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends request with headers defined in the OpenAPI specs removed.
 */
@Singleton
@HeaderFuzzer
public class RemoveHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RemoveHeadersFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     */
    public RemoveHeadersFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getHeaders().isEmpty()) {
            logger.skip("No headers to fuzz");
            return;
        }
        Set<Set<CatsHeader>> headersCombination = FuzzingData.SetFuzzingStrategy.powerSet(data.getHeaders());
        Set<CatsHeader> mandatoryHeaders = data.getHeaders().stream().filter(CatsHeader::isRequired).collect(Collectors.toSet());

        for (Set<CatsHeader> headersSubset : headersCombination) {
            boolean anyMandatoryHeaderRemoved = this.isAnyMandatoryHeaderRemoved(headersSubset, mandatoryHeaders);

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .logger(logger)
                            .fuzzer(this)
                            .fuzzingData(data)
                            .headers(headersSubset)
                            .scenario("Send only the following headers: %s plus any authentication headers.".formatted(headersSubset))
                            .expectedResponseCode(ResponseCodeFamily.getResultCodeBasedOnRequiredFieldsRemoved(anyMandatoryHeaderRemoved))
                            .expectedResult(" as mandatory headers [%s] removed".formatted(anyMandatoryHeaderRemoved ? "were" : "were not"))
                            .addUserHeaders(false)
                            .build()
            );
        }
    }

    private boolean isAnyMandatoryHeaderRemoved(Set<CatsHeader> headersSubset, Set<CatsHeader> requiredHeaders) {
        Set<CatsHeader> intersection = new HashSet<>(requiredHeaders);
        intersection.retainAll(headersSubset);
        return intersection.size() != requiredHeaders.size();
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "iterate through each header and remove different combinations of them";
    }
}
