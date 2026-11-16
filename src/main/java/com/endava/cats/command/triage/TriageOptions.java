package com.endava.cats.command.triage;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Triage settings supplied on the command line.
 *
 * @param errorsOnly  triage error tests only; warnings are triaged by default, like the problems of the HTML report
 * @param verify      replay one representative request per occurrence against the server
 * @param serverUrl   optional base URL override for {@code --verify}
 * @param headers     header overrides for {@code --verify}
 * @param environment resolves masked {@code $$VAR} report placeholders (normally {@link System#getenv(String)})
 */
public record TriageOptions(boolean errorsOnly,
                            boolean verify,
                            String serverUrl,
                            Map<String, String> headers,
                            UnaryOperator<String> environment) {
}
