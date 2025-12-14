package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;

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

    private static final List<String> COMMAND_ERROR_PATTERNS = List.of(
            "root:",
            "/bin/bash",
            "/bin/sh",
            "uid=",
            "gid=",
            "groups=",
            "linux",
            "darwin",
            "windows",
            "total ",
            "drwx",
            "-rw-",
            "volume serial number",
            "directory of",
            "command not found",
            "not recognized as an internal",
            "sh:",
            "bash:",
            "/etc/passwd",
            "permission denied",
            "cannot access",
            "no such file",
            "syntax error",
            "unexpected token",
            "bin/",
            "sbin/",
            "usr/",
            "home/",
            "var/",
            "etc/",
            "system32",
            "program files"
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
    protected List<String> getErrorPatterns() {
        return COMMAND_ERROR_PATTERNS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send OS command injection payloads to detect command injection vulnerabilities";
    }
}
