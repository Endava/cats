package com.endava.cats.dsl.impl;

import com.endava.cats.args.AuthArguments;
import com.endava.cats.dsl.Parser;
import com.endava.cats.util.CatsException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

public class AuthScriptProviderParser implements Parser {
    private final AuthArguments authArguments;
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(AuthScriptProviderParser.class);
    private long t0 = System.currentTimeMillis();
    private String existingValue;

    public AuthScriptProviderParser(AuthArguments authArguments) {
        this.authArguments = authArguments;
    }

    @Override
    public String parse(String expression, String payload) {
        if (authArguments.getAuthRefreshInterval() > 0 && refreshIntervalElapsed()) {
            logger.debug("Refresh interval passed.");
            t0 = System.currentTimeMillis();
            existingValue = runScript();
        }

        if (existingValue == null) {
            existingValue = runScript();
        }
        return existingValue;
    }

    private String runScript() {
        logger.info("Running script {} to get credentials", authArguments.getAuthRefreshScript());
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(authArguments.getAuthRefreshScript());
            processBuilder.redirectErrorStream(true);
            StringBuilder builder = new StringBuilder();

            try (BufferedReader reader = processBuilder.start().inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }

            return builder.toString();
        } catch (Exception e) {
            throw new CatsException(e);
        }
    }

    private boolean refreshIntervalElapsed() {
        return (System.currentTimeMillis() - t0) / 1000 >= authArguments.getAuthRefreshInterval();
    }
}
