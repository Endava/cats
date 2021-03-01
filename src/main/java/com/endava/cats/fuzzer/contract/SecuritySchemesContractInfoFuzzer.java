package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ContractInfoFuzzer
@Component
public class SecuritySchemesContractInfoFuzzer extends BaseContractInfoFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    protected SecuritySchemesContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path has security schemes defined either globally or at path level");
        testCaseListener.addExpectedResult(log, "[at least a security schemes] must be present either globally or at path level");

        Map<String, SecurityScheme> securitySchemeMap = Optional.ofNullable(data.getOpenApi().getComponents()).orElse(new Components()).getSecuritySchemes();
        List<SecurityRequirement> securityRequirementList = Optional.ofNullable(data.getOpenApi().getSecurity()).orElse(Collections.emptyList());

        boolean hasTopLevelSecuritySchemes = !securityRequirementList.isEmpty();
        boolean areGlobalSecuritySchemesDefined = securityRequirementList.stream().allMatch(securityRequirement -> securitySchemeMap.keySet().containsAll(securityRequirement.keySet()));

        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());

        boolean hasSecuritySchemesAtTagLevel = !Optional.ofNullable(operation.getSecurity()).orElse(Collections.emptyList()).isEmpty();

        if (hasTopLevelSecuritySchemes || hasSecuritySchemesAtTagLevel) {
            if (areGlobalSecuritySchemesDefined) {
                testCaseListener.reportInfo(log, "The current path has security scheme(s) properly defined");
            } else {
                testCaseListener.reportWarn(log, "The current path has security scheme(s) defined, but they are not present in the [components->securitySchemes] contract element");
            }
        } else {
            testCaseListener.reportError(log, "The current path does not have security scheme(s) defined and there are none defined globally");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies if the OpenApi contract contains valid security schemas for all paths, either globally configured or per path";
    }
}
