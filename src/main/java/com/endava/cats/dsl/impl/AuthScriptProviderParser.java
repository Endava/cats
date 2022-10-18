package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.exception.CatsException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AuthScriptProviderParser implements Parser {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(AuthScriptProviderParser.class);
    private long t0 = System.currentTimeMillis();
    private String existingValue;


    @Override
    public String parse(String expression, Object context) {
        Map<String, String> authParams = (Map<String, String>) context;
        String script = authParams.get("auth_script");
        int authRefreshInterval = Integer.parseInt(authParams.getOrDefault("refresh_interval", "0"));

        if (authRefreshInterval > 0 && refreshIntervalElapsed(authRefreshInterval)) {
            logger.debug("Refresh interval passed.");
            t0 = System.currentTimeMillis();
            existingValue = runScript(script);
        }

        if (existingValue == null) {
            existingValue = runScript(script);
        }
        return existingValue;
    }

    private String runScript(String script) {
        logger.info("Running script {} to get credentials", script);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(script);
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

    private boolean refreshIntervalElapsed(int authRefreshInterval) {
        return (System.currentTimeMillis() - t0) / 1000 >= authRefreshInterval;
    }
}
