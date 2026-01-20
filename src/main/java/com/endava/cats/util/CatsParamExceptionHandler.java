package com.endava.cats.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import picocli.CommandLine;

public class CatsParamExceptionHandler implements CommandLine.IParameterExceptionHandler {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CatsParamExceptionHandler.class);

    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) {
        logger.fatal(AnsiUtils.red("{}"), ex.getMessage());
        logger.fatal(AnsiUtils.bold("Try cats --help for the full list of arguments"));

        return 191;
    }
}
