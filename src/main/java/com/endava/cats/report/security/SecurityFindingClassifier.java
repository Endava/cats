package com.endava.cats.report.security;

import com.endava.cats.fuzzer.http.BypassAuthenticationFuzzer;
import com.endava.cats.fuzzer.http.CustomHttpMethodsFuzzer;
import com.endava.cats.fuzzer.http.HttpMethodsFuzzer;
import com.endava.cats.fuzzer.http.InsecureDirectObjectReferencesFuzzer;
import com.endava.cats.fuzzer.http.NonRestHttpMethodsFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FindingEvidence;
import com.endava.cats.model.SecurityFindingType;
import com.endava.cats.util.ConsoleUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Recognizes security evidence without depending on WFC codes or promoting its confidence. */
public final class SecurityFindingClassifier {
    private static final List<Class<?>> UNDOCUMENTED_METHOD_FUZZERS = List.of(
            HttpMethodsFuzzer.class, NonRestHttpMethodsFuzzer.class, CustomHttpMethodsFuzzer.class);

    private SecurityFindingClassifier() {
    }

    public static boolean isHighSeverity(FindingEvidence evidence) {
        return Stream.of(SecurityFindingType.fromReason(evidence.reason()), fromFuzzer(evidence))
                .flatMap(Optional::stream).anyMatch(SecurityFindingType::isHighSeverity);
    }

    public static Optional<SecurityFindingType> fromFuzzer(FindingEvidence evidence) {
        if (!ResponseCodeFamily.is2xxCode(evidence.responseCode())) {
            return Optional.empty();
        }
        if (ConsoleUtils.matchesFuzzer(evidence.fuzzer(), BypassAuthenticationFuzzer.class)) {
            return Optional.of(HttpMethod.isResourceModificationMethod(evidence.httpMethod())
                    ? SecurityFindingType.ANONYMOUS_MODIFICATIONS : SecurityFindingType.DECLARED_AUTH_NOT_ENFORCED);
        }
        if (isUndocumentedMethodFuzzer(evidence.fuzzer())) {
            return Optional.of(SecurityFindingType.HIDDEN_ENDPOINT);
        }
        return ConsoleUtils.matchesFuzzer(evidence.fuzzer(), InsecureDirectObjectReferencesFuzzer.class)
                ? Optional.of(SecurityFindingType.INCONSISTENT_WRITE_AUTHORIZATION) : Optional.empty();
    }

    public static boolean isUndocumentedMethodFuzzer(String fuzzer) {
        return ConsoleUtils.matchesAnyFuzzer(fuzzer, UNDOCUMENTED_METHOD_FUZZERS);
    }
}
