package com.endava.cats.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.config.level.PrettyLevel;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

public class GlobalLoggingExtension implements QuarkusTestBeforeEachCallback {

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        CatsUtil.setCatsLogLevel("ALL");
        PrettyLogger.enableLevels(PrettyLevel.STAR, PrettyLevel.NONE, PrettyLevel.INFO, PrettyLevel.TIMER, PrettyLevel.FATAL);
    }
}
