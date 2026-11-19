package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.RobustnessResponseReporter;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.HttpHeaders;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sends the operation's declared {@code Content-Type} with conflicting duplicate {@code charset}
 * and {@code boundary} parameters to expose proxy-vs-origin media-type parser differentials and unhandled exceptions.
 */
@Singleton
@HeaderFuzzer
public class ContentTypeParameterDifferentialHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor executor;
    private final RobustnessResponseReporter reporter;
    private final Set<OperationMediaType> visited = new HashSet<>();

    public ContentTypeParameterDifferentialHeadersFuzzer(SimpleExecutor executor, RobustnessResponseReporter reporter) {
        this.executor = executor;
        this.reporter = reporter;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!isApplicableTo(data)) {
            return;
        }
        String baseType = extractBaseMediaType(data.getFirstRequestContentType());
        if (visited.add(new OperationMediaType(data.getMethod(), data.getPath(), baseType))) {
            buildParameterVariants(baseType).forEach(variant -> executeProbe(data, variant));
        }
    }

    @Override
    public boolean isApplicableTo(FuzzingData data) {
        return data != null
                && data.getMethod() != null
                && HttpMethod.requiresBody(data.getMethod())
                && !JsonUtils.isEmptyPayload(data.getPayload());
    }

    static String extractBaseMediaType(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return "application/json";
        }
        int semicolon = contentType.indexOf(';');
        String base = (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim();
        return base.isEmpty() ? "application/json" : base;
    }

    static List<String> buildParameterVariants(String baseType) {
        String token = CatsRandom.alphanumeric(4).toLowerCase(Locale.ROOT);
        return List.of(
                "%s; charset=utf-8; charset=utf-16be".formatted(baseType),
                "%s; charset=\"utf-8\"; charset=iso-8859-1; v=%s".formatted(baseType, token),
                "%s; charset*=UTF-8''utf-8; charset=ibm500".formatted(baseType),
                "%s; boundary=; boundary=cats-%s".formatted(baseType, token),
                "%s; boundary=\";boundary=cats-%s\"; charset=utf-8".formatted(baseType, token));
    }

    private void executeProbe(FuzzingData data, String contentTypeValue) {
        executor.execute(SimpleExecutorContext.builder()
                .fuzzingData(data)
                .fuzzer(this)
                .logger(logger)
                .scenario("Send Content-Type header with differential parameters [%s].".formatted(contentTypeValue))
                .headers(withContentTypeHeader(data, contentTypeValue))
                .mutationTarget(RequestTarget.header(HttpHeaders.CONTENT_TYPE))
                .responseProcessor((response, context) -> reporter.report(logger, context, response))
                .expectedResponseCode(RobustnessResponseReporter.expectedResponses())
                .matchResponseResult(false)
                .matchResponseContentType(false)
                .build());
    }

    private static Collection<CatsHeader> withContentTypeHeader(FuzzingData data, String value) {
        Stream<CatsHeader> retained = Optional.ofNullable(data.getHeaders()).orElse(Set.of()).stream()
                .filter(header -> !HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getName()));
        return Stream.concat(retained, Stream.of(CatsHeader.from(HttpHeaders.CONTENT_TYPE, value, true, null))).toList();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.OPTIONS);
    }

    @Override
    public String description() {
        return "sends Content-Type headers with conflicting duplicate charset and boundary parameters to detect media-type parser differentials";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }

    private record OperationMediaType(HttpMethod method, String path, String baseType) {
    }
}
