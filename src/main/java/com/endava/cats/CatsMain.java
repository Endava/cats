package com.endava.cats;

import com.endava.cats.command.CatsCommand;
import com.endava.cats.exception.CatsException;
import com.endava.cats.util.CatsParamExceptionHandler;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

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

        CommandLine commandLine = new CommandLine(catsCommand, factory)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setColorScheme(colorScheme())
                .setAbbreviatedOptionsAllowed(true)
                .setAbbreviatedSubcommandsAllowed(true)
                .setParameterExceptionHandler(new CatsParamExceptionHandler());

        loadConfigIfSupplied(commandLine, args);
        return commandLine.execute(args);
    }

    private void loadConfigIfSupplied(CommandLine commandLine, String... args) {
        File configFile = findConfigFile(args);
        if (configFile != null && configFile.exists()) {
            logger.config("Loading config from {}", configFile.getAbsolutePath());
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile.toPath())) {
                props.load(in);
                commandLine.setDefaultValueProvider(new CommandLine.PropertiesDefaultProvider(props));
            } catch (Exception e) {
                throw new CatsException(e);
            }
        }
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

    private File findConfigFile(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--configFile".equals(args[i])) {
                return new File(args[i + 1]);
            } else if (args[i].startsWith("--configFile=")) {
                return new File(args[i].split("=", 2)[1]);
            }
        }
        return null;
    }
}