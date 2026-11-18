package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.generator.simple.EncodedAuthenticationGenerator;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestProvenance;
import com.endava.cats.model.RequestTarget;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Request-only exemption for exact, public encoded-auth probes; never a general exemption for mutated credentials. */
public final class SyntheticAuthHeaderPolicy {
    private static final Set<String> FUZZERS = Set.of("EncodedAuthenticationHeaders", "EncodedAuthenticationHeadersFuzzer");

    private SyntheticAuthHeaderPolicy() {
    }

    public static boolean isProbe(CatsTestCase testCase) {
        return testCase != null && testCase.getFuzzer() != null && FUZZERS.contains(testCase.getFuzzer()) && testCase.getRequest() != null
                && Optional.ofNullable(testCase.getRequestProvenance()).map(RequestProvenance::getMutationTargets).orElse(List.of())
                .stream().anyMatch(SyntheticAuthHeaderPolicy::isAuthorizationMutation)
                && Optional.ofNullable(testCase.getRequest().getHeaders()).orElse(List.of()).stream().anyMatch(SyntheticAuthHeaderPolicy::isPublicHeader);
    }

    public static boolean mayShow(CatsTestCase testCase, KeyValuePair<String, ?> header, ReportingArguments arguments) {
        return isProbe(testCase) && isPublicHeader(header) && Optional.ofNullable(arguments.getMaskHeaders()).orElse(Set.of())
                .stream().noneMatch(name -> name.equalsIgnoreCase(header.getKey()));
    }

    public static boolean isPublicHeader(KeyValuePair<String, ?> header) {
        return header != null && "Authorization".equalsIgnoreCase(header.getKey()) && EncodedAuthenticationGenerator.isSyntheticValue(header.getValue());
    }

    private static boolean isAuthorizationMutation(RequestTarget target) {
        return target.location() == RequestTarget.Location.HEADER && "Authorization".equalsIgnoreCase(target.name());
    }
}
