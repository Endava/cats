package com.endava.cats.command.triage;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Report-wide observations gathered in a first pass over all tests, so classification of each test does not depend
 * on the order in which failing tests are visited.
 *
 * @param pathsWithSuccessfulPost contract paths with at least one {@code 2XX} {@code POST}
 * @param firstDeleteTestByPath   test number of the first successful {@code DELETE} per contract path
 * @param minedRefData            live values mined from {@code 2XX} exchanges
 * @param rulesByTestNumber       synthesized correlation rule per test number
 * @param pathsWithRules          contract paths that received at least one synthesized rule
 * @param drift                   undocumented status-code observations
 * @param openAPI                 the optional contract
 */
record ClassificationContext(Set<String> pathsWithSuccessfulPost,
                             Map<String, Integer> firstDeleteTestByPath,
                             Map<String, Map<String, Object>> minedRefData,
                             Map<Integer, SynthesizedCorrelationRule> rulesByTestNumber,
                             Set<String> pathsWithRules,
                             ContractDriftDetector drift,
                             OpenAPI openAPI) {

    Optional<SynthesizedCorrelationRule> ruleFor(TriageTestCase testCase) {
        return Optional.ofNullable(rulesByTestNumber.get(testCase.number()));
    }
}
