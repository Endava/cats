package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

/**
 * Fuzzer that sends SSTI injection payloads in string fields.
 */
@Singleton
@FieldFuzzer
public class SstiInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {

    private static final String CALC_EXPR = "83*97";
    private static final String CALC_RESULT = "8051";
    private static final String ALT_CALC_EXPR = "73*89";
    private static final String ALT_CALC_RESULT = "6497";

    private static final List<String> TOP_PAYLOADS = List.of(
            "{{" + CALC_EXPR + "}}",
            "${" + CALC_EXPR + "}",
            "#{" + CALC_EXPR + "}",
            "*{" + CALC_EXPR + "}",
            "<%= " + CALC_EXPR + " %>"
    );

    private static final List<String> ALL_PAYLOADS = List.of(
            // Jinja2, Twig, Tornado (Python, PHP)
            "{{" + CALC_EXPR + "}}",
            "{{" + ALT_CALC_EXPR + "}}",
            "{{" + CALC_EXPR + "*1}}",

            // Freemarker, Velocity, Thymeleaf (Java)
            "${" + CALC_EXPR + "}",
            "${{" + CALC_EXPR + "}}",
            "[[${" + CALC_EXPR + "}]]",
            "${" + ALT_CALC_EXPR + "}",

            // Expression Language (Java)
            "#{" + CALC_EXPR + "}",
            "#{ " + CALC_EXPR + " }",

            // Spring (Java)
            "*{" + CALC_EXPR + "}",
            "@{" + CALC_EXPR + "}",

            // ERB, Slim (Ruby)
            "<%= " + CALC_EXPR + " %>",
            "<%= " + ALT_CALC_EXPR + " %>",

            // Razor (C#/.NET)
            "@(" + CALC_EXPR + ")",

            // Smarty (PHP)
            "{" + CALC_EXPR + "}",
            "{$smarty.version}",

            // Mako (Python)
            "${" + CALC_EXPR + "}",

            // Groovy (Java)
            "${=" + CALC_EXPR + "}",

            // Pebble (Java)
            "{{ " + CALC_EXPR + " }}",

            // Additional detection vectors
            "{{" + CALC_EXPR + "}}{{" + CALC_EXPR + "}}", // Doubled
            "${" + CALC_EXPR + "}${" + CALC_EXPR + "}"     // Doubled
    );

    private static final List<String> SSTI_ERROR_KEYWORDS = List.of(
            "template",
            "expression",
            "velocity",
            "freemarker",
            "thymeleaf",
            "jinja",
            "jinja2",
            "twig",
            "handlebars",
            "mustache",
            "mako",
            "pebble",
            "groovy",
            "erb",
            "razor",
            "smarty",
            "tornado",
            "engine",
            "parsing error",
            "syntax error",
            "render error",
            "compilation error",
            "undefined variable",
            "template not found"
    );

    public SstiInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "SSTI";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_PAYLOADS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send SSTI injection payloads to detect Server-Side Template Injection vulnerabilities";
    }

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String responseBody = response.getBody().toLowerCase(Locale.ROOT);
        String originalBody = response.getBody();

        for (String keyword : SSTI_ERROR_KEYWORDS) {
            if (responseBody.contains(keyword)) {
                return InjectionDetectionResult.vulnerable(
                        "SSTI injection error detected",
                        "Response contains template engine error keyword: '" + keyword + "'"
                );
            }
        }

        boolean hasMainResult = originalBody.contains(CALC_RESULT);
        boolean hasAltResult = originalBody.contains(ALT_CALC_RESULT);

        if (hasMainResult || hasAltResult) {
            String detectedResult = hasMainResult ? CALC_RESULT : ALT_CALC_RESULT;

            if (looksLikeEvaluatedExpression(originalBody, detectedResult)) {
                return InjectionDetectionResult.vulnerable(
                        "Potential SSTI vulnerability detected",
                        "Response contains '" + detectedResult + "' which appears to be the evaluated result of the injected calculation. " +
                                "The number appears in a context suggesting template evaluation rather than normal data."
                );
            }
        }

        if (hasReflectedPayloadWithTemplateIndicators(originalBody)) {
            return InjectionDetectionResult.vulnerable(
                    "Potential SSTI reflection detected",
                    "Payload was reflected in response with template-related context, suggesting possible template processing"
            );
        }

        return InjectionDetectionResult.notVulnerable();
    }

    /**
     * Checks if the result appears in a context that suggests it's an evaluated expression
     * rather than legitimate business data (like an ID or count).
     */
    private boolean looksLikeEvaluatedExpression(String body, String result) {
        String pattern = "(?<![0-9])" + result + "(?![0-9])";
        if (!body.matches(".*" + pattern + ".*")) {
            return false;
        }

        int index = body.indexOf(result);
        if (index > 0 && index < body.length() - result.length()) {
            String before = body.substring(Math.max(0, index - 5), index);
            String after = body.substring(index + result.length(),
                    Math.min(body.length(), index + result.length() + 5));

            String context = before + result + after;
            if (context.matches(".*[\"':>\\s]" + result + "[\"',<\\s}].*")) {
                return true;
            }
        }

        return true;
    }

    /**
     * Checks if a payload was reflected with template-related indicators nearby.
     */
    private boolean hasReflectedPayloadWithTemplateIndicators(String body) {
        String bodyLower = body.toLowerCase(Locale.ROOT);

        boolean hasCalcExpr = bodyLower.contains(CALC_EXPR.toLowerCase(Locale.ROOT)) ||
                bodyLower.contains(ALT_CALC_EXPR.toLowerCase(Locale.ROOT));

        if (hasCalcExpr) {
            for (String keyword : SSTI_ERROR_KEYWORDS) {
                if (bodyLower.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }
}