package com.endava.cats;

import com.endava.cats.command.CatsCommand;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.exception.CatsException;
import com.endava.cats.util.CatsParamExceptionHandler;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Main application entry point.
 */
@QuarkusMain
public class CatsMain implements QuarkusApplication {
    private static final List<String> DEFAULT_CONFIG_FILE_NAMES = List.of(".cats.properties");

    @Inject
    CatsCommand catsCommand;

    @Inject
    ConfigOptions configOptions;

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
        if (configOptions != null) {
            configOptions.setResolvedConfigFile(null);
        }
        File configFile = findConfigFile(args);
        if (configFile != null && configFile.exists()) {
            if (configOptions != null) {
                configOptions.setResolvedConfigFile(configFile.toPath().toAbsolutePath().normalize());
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile.toPath())) {
                props.load(in);
                for (String key : List.copyOf(props.stringPropertyNames())) {
                    if (key.contains(".") && !key.startsWith("cats.")) {
                        props.putIfAbsent("cats." + key, props.getProperty(key));
                    }
                }
                if (extractOptionValue(args, "--configFile") == null) {
                    for (String key : List.copyOf(props.stringPropertyNames())) {
                        String lower = key.toLowerCase(Locale.ROOT);
                        if ("authrefreshscript".equals(lower) || lower.endsWith(".authrefreshscript")) {
                            props.remove(key);
                        }
                    }
                }
                CommandLine.IDefaultValueProvider provider = new CommandLine.PropertiesDefaultProvider(props);
                commandLine.setDefaultValueProvider(provider);
            } catch (Exception e) {
                throw new CatsException(e);
            }
        }
    }

    private void checkForConsoleColorsDisabled(String... args) {
        boolean colorsDisabled = Arrays.stream(args).anyMatch(arg -> arg.trim().toLowerCase(Locale.ROOT).equals("--no-color"));
        if (colorsDisabled) {
            PrettyLogger.disableColors();
            Ansi.setEnabled(false);
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

    File findConfigFile(String[] args) {
        String explicitConfig = extractOptionValue(args, "--configFile");
        if (explicitConfig != null) {
            return new File(explicitConfig);
        }

        String contractArg = extractOptionValue(args, "-c", "--contract");
        if (contractArg != null && !contractArg.startsWith("http://") && !contractArg.startsWith("https://")) {
            try {
                Path contractParent = Path.of(contractArg).toAbsolutePath().getParent();
                File fromContractDir = findDefaultPropertiesInDirectory(contractParent);
                if (fromContractDir != null) {
                    return fromContractDir;
                }
            } catch (InvalidPathException _) {
                // ignore malformed contract path and fall back to working/home directory
            }
        }

        return findDefaultPropertiesInDirectory(Path.of(System.getProperty("user.dir", ".")));
    }

    private static String extractOptionValue(String[] args, String... optionNames) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            for (String opt : optionNames) {
                if (opt.equals(arg) && i + 1 < args.length) {
                    return args[i + 1];
                }
                if (arg.startsWith(opt + "=")) {
                    return arg.substring(opt.length() + 1);
                }
            }
        }
        return null;
    }

    private static File findDefaultPropertiesInDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        for (String fileName : DEFAULT_CONFIG_FILE_NAMES) {
            Path candidate = directory.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
        }
        return null;
    }
}