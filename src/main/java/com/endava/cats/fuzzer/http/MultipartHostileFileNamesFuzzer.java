package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.io.util.MultipartMutation;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fuzzer that sends multipart requests in which a file part has a hostile file name, written to the wire as is.
 */
@Singleton
@HttpFuzzer
public class MultipartHostileFileNamesFuzzer extends BaseMultipartFuzzer {
    private static final int MAX_PRINTABLE_LENGTH = 40;
    private static final List<String> HOSTILE_NAMES = List.of(
            "../../../../etc/passwd.pdf",
            "..\\..\\..\\windows\\win.ini.pdf",
            "/etc/passwd.pdf",
            "C:\\Windows\\win.ini.pdf",
            "evidence\u0000.pdf",
            "evidence\r\nX-Injected: true.pdf",
            "a".repeat(300) + ".pdf",
            "...",
            "   ",
            "evidence\u202Efdp.exe",
            "evidence.pdf.exe");

    /**
     * Creates a new MultipartHostileFileNamesFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public MultipartHostileFileNamesFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected ResponseCodeFamily expectedResponseCode(FuzzingData data) {
        return ResponseCodeFamilyPredefined.FOURXX_TWOXX;
    }

    @Override
    protected List<MultipartCase> cases(FuzzingData data) {
        List<MultipartCase> cases = new ArrayList<>();
        for (String name : filePartNames(data)) {
            for (String hostileName : HOSTILE_NAMES) {
                cases.add(new MultipartCase("Send a multipart request with the [" + name + "] file named ["
                        + printable(hostileName) + "]", withFileName(name, hostileName)));
            }
        }
        return cases;
    }

    private static MultipartMutation withFileName(String name, String hostileName) {
        return encoded -> {
            for (int index = 0; index < encoded.parts().size(); index++) {
                MultipartEncoder.Part part = encoded.parts().get(index);
                if (part.name().equals(name) && part.isFile()) {
                    String header = "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + hostileName
                            + "\"\r\nContent-Type: " + part.contentType() + "\r\n";
                    byte[] body = MultipartEncoder.render(encoded.boundary(), encoded.parts(), Map.of(index, header));
                    return withBody(encoded, body, encoded.contentType());
                }
            }
            return encoded;
        };
    }

    private static String printable(String hostileName) {
        String printable = hostileName.replace("\u0000", "\\0")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\u202E", "\\u202E");
        return hostileName.length() > MAX_PRINTABLE_LENGTH ? hostileName.substring(0, MAX_PRINTABLE_LENGTH - 3) + "..."
                : printable;
    }

    @Override
    public String description() {
        return "send a multipart request with hostile file names such as path traversal, absolute paths, "
                + "control characters, very long names and double extensions";
    }
}
