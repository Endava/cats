package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.RuntimeResourcePool;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Retrieves a resource created by a successful POST using a same-origin Location that matches an in-scope item GET.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class CreatedResourceLocationFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CreatedResourceLocationFuzzer.class);
    private final SimpleExecutor executor;
    private final RuntimeResourcePool pool;
    private final ApiArguments apiArguments;
    private final FilterArguments filterArguments;
    private final TestCaseListener listener;

    /** Creates a location-based retrieval check using the observed exchanges and current API scope. */
    public CreatedResourceLocationFuzzer(SimpleExecutor executor, RuntimeResourcePool pool,
                                         ApiArguments apiArguments, FilterArguments filterArguments, TestCaseListener listener) {
        this.executor = executor;
        this.pool = pool;
        this.apiArguments = apiArguments;
        this.filterArguments = filterArguments;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data.getMethod() != HttpMethod.POST) {
            return;
        }
        if (!filterArguments.isHttpMethodSupplied(HttpMethod.GET) || data.getOpenApi() == null) {
            logger.skip("No in-scope GET is available to retrieve a resource created by POST {}", data.getPath());
            return;
        }
        Optional<String> location = pool.successfulExchanges(data.getPath(), HttpMethod.POST).stream()
                .filter(this::isSynchronousCreationWithLocation)
                .map(exchange -> ObservedHttpTarget.resolvedLocation(exchange.url(), exchange.location())
                        .flatMap(url -> ObservedHttpTarget.relativePath(apiArguments, url)))
                .flatMap(Optional::stream)
                .filter(path -> isSelectedDocumentedItemGet(data, path))
                .findFirst();
        if (location.isEmpty()) {
            logger.skip("No POST 201 Location for {} resolves to a selected same-origin item GET", data.getPath());
            return;
        }
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data).httpMethod(HttpMethod.GET)
                .path(location.get()).payload("{}").replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Retrieve the resource created by POST using its Location header")
                .responseProcessor(this::checkResponse).build());
    }

    private boolean isSynchronousCreationWithLocation(RuntimeResourcePool.SuccessfulExchange exchange) {
        return exchange.status() == 201 && exchange.location() != null;
    }

    /** Requires the Location to match an in-scope GET operation for an item directly below the POST path. */
    private boolean isSelectedDocumentedItemGet(FuzzingData data, String relativePath) {
        return filterArguments.getPathsToRun(data.getOpenApi()).stream()
                .filter(template -> template.startsWith(data.getContractPath() + "/{"))
                .filter(template -> data.getOpenApi().getPaths().get(template).getGet() != null)
                .anyMatch(template -> ObservedHttpTarget.matchesTemplate(template, relativePath));
    }

    private void checkResponse(CatsResponse response, FuzzingData data) {
        if (response.getResponseCode() == 404 || response.getResponseCode() == 410) {
            listener.reportResultWarn(logger, data, "Created resource unavailable", "POST returned 201 with a Location for a documented GET, but the resource is not available (status {})",
                    response.responseCodeAsString());
        } else if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
            listener.reportResultInfo(logger, data, "Resource returned by Location is available (status {})", response.responseCodeAsString());
        } else {
            listener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX, false);
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "retrieve a newly created resource from a same-origin Location pointing to a documented GET";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
