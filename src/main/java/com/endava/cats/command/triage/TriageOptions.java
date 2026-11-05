package com.endava.cats.command.triage;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Triage settings supplied on the command line.
 *
 * @param includeWarnings cluster warning test cases in addition to errors
 * @param verify          replay one representative request per cluster against the server
 * @param serverUrl       optional base URL override for {@code --verify}
 * @param headers         header overrides for {@code --verify}
 * @param environment     resolves masked {@code $$VAR} report placeholders (normally {@link System#getenv(String)})
 */
public record TriageOptions(boolean includeWarnings,
                            boolean verify,
                            String serverUrl,
                            Map<String, String> headers,
                            UnaryOperator<String> environment) {
}
