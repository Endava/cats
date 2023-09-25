package com.endava.cats;

import com.endava.cats.command.CatsCommand;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

import jakarta.inject.Inject;

/**
 * We need to print the build version and build time
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

        return new CommandLine(catsCommand, factory)
                .setCaseInsensitiveEnumValuesAllowed(true).setColorScheme(colorScheme())
                .execute(args);
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