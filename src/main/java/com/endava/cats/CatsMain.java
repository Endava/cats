package com.endava.cats;

import com.endava.cats.command.CatsCommand;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Locale;

/**
 * Main application entry point.
 */
@QuarkusMain
public class CatsMain implements QuarkusApplication {

    @Inject
    CatsCommand catsCommand;

    @Inject
    CommandLine.IFactory factory;

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CatsMain.class);

    @Override
    public int run(String... args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logger.fatal("Something unexpected happened: {}", e.getMessage());
            logger.debug("Stacktrace", e);
        });
        checkForConsoleColorsDisabled(args);

        return new CommandLine(catsCommand, factory)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setColorScheme(colorScheme())
                .setAbbreviatedOptionsAllowed(true)
                .setAbbreviatedSubcommandsAllowed(true)
                .execute(args);
    }

    private void checkForConsoleColorsDisabled(String... args) {
        boolean colorsDisabled = Arrays.stream(args).anyMatch(arg -> arg.trim().toLowerCase(Locale.ROOT).equals("--no-color"));
        if (colorsDisabled) {
            PrettyLogger.disableColors();
            PrettyLogger.changeMessageFormat("%1$-12s");
        }
    }

    CommandLine.Help.ColorScheme colorScheme() {
        return new CommandLine.Help.ColorScheme.Builder()
                .commands(CommandLine.Help.Ansi.Style.bold)
                .options(CommandLine.Help.Ansi.Style.fg_yellow)
                .parameters(CommandLine.Help.Ansi.Style.fg_yellow)
                .optionParams(CommandLine.Help.Ansi.Style.faint)
                .errors(CommandLine.Help.Ansi.Style.fg_red, CommandLine.Help.Ansi.Style.bold)
                .stackTraces(CommandLine.Help.Ansi.Style.italic)
                .build();
    }
}