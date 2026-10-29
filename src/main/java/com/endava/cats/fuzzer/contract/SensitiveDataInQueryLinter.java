package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.fuzzer.contract.base.BaseLinter;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Flags query parameters that expose credentials, secrets, or sensitive PII in the URL query string.
 */
@Linter
@Singleton
public class SensitiveDataInQueryLinter extends BaseLinter {
    private static final Set<String> SENSITIVE_NORMALIZED_NAMES = Set.of(
            "password", "passwd", "passcode", "pwd", "secret", "clientsecret", "apikey",
            "accesstoken", "refreshtoken", "idtoken", "authtoken", "bearertoken", "sessiontoken",
            "privatekey", "ssn", "socialsecuritynumber", "cardnumber", "creditcard", "creditcardnumber",
            "cvv", "cvc", "cvv2", "pin", "pinblock"
    );
    private static final Set<String> SENSITIVE_SUFFIXES = Set.of(
            "password", "passwd", "secret", "apikey", "privatekey", "accesstoken", "refreshtoken",
            "authtoken", "idtoken", "cardnumber", "ssn", "cvv", "cvc"
    );

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new SensitiveDataInQueryLinter instance.
     *
     * @param tcl the test case listener
     */
    public SensitiveDataInQueryLinter(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if query parameters expose credentials, tokens, or sensitive PII in the URL for {} {}", data.getMethod(), data.getPath());
        testCaseListener.addExpectedResult(log, "Credentials, secrets, and sensitive PII must not be passed in URL query parameters");

        Set<String> sensitiveParams = collectSensitiveQueryParameters(data);

        if (sensitiveParams.isEmpty()) {
            testCaseListener.reportResultInfo(log, data, "No sensitive credentials or PII found in query parameters");
        } else {
            testCaseListener.reportResultWarn(log, data, "Sensitive data exposed in URL query parameters",
                    "The following URL query parameters appear to carry sensitive data: {}", sensitiveParams);
        }
    }

    private Set<String> collectSensitiveQueryParameters(FuzzingData data) {
        Set<String> candidates = new LinkedHashSet<>();
        if (data.getQueryParams() != null) {
            candidates.addAll(data.getQueryParams());
        }

        PathItem pathItem = data.getPathItem();
        Operation operation = pathItem != null && data.getMethod() != null
                ? HttpMethod.getOperation(data.getMethod(), pathItem)
                : null;

        for (Parameter resolved : OpenApiUtils.collectPathAndOperationParameters(pathItem, operation, data.getOpenApi())) {
            if (StringUtils.isNotBlank(resolved.getName()) && "query".equalsIgnoreCase(resolved.getIn())) {
                candidates.add(resolved.getName());
            }
        }

        Set<String> flagged = new LinkedHashSet<>();
        for (String paramName : candidates) {
            if (isSensitiveParamName(paramName)) {
                flagged.add(paramName);
            }
        }
        return flagged;
    }

    static boolean isSensitiveParamName(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        String normalized = WordUtils.normalizeAlphaNumeric(name);
        if (SENSITIVE_NORMALIZED_NAMES.contains(normalized)) {
            return true;
        }
        return SENSITIVE_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "flags query parameters whose names indicate credentials, secrets, or sensitive PII (such as password, secret, apiKey, accessToken, ssn, cvv, cardNumber)";
    }
}
