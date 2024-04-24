package com.endava.cats.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class for console-related operations.
 */
public abstract class ConsoleUtils {

    private static final String QUARKUS_PROXY_SUFFIX = "_Subclass";
    private static final String REGEX_TO_REMOVE_FROM_FUZZER_NAMES = "TrimValidate|ValidateTrim|SanitizeValidate|ValidateSanitize|Fuzzer";

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getConsoleLogger();

    private static final Pattern ANSI_REMOVE_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    /**
     * -- GETTER --
     * Get the width of the terminal.
     * <p>
     * Size is cached, so it won't reach to width changes during run.
     */
    @Getter
    private static int terminalWidth = 80;

    private ConsoleUtils() {
        //ntd
    }

    public static void initTerminalWidth(CommandLine.Model.CommandSpec spec) {
        try {
            terminalWidth = spec.usageMessage().width();
        } catch (Exception e) {
            terminalWidth = 80;
        }
    }

    /**
     * Center a string with ANSI color, applying padding and using the specified color.
     *
     * @param str     The input string.
     * @param padding The padding size.
     * @param color   The ANSI color to apply.
     * @return The centered string with ANSI color.
     */
    public static String centerWithAnsiColor(String str, int padding, Ansi.Color color) {
        String strAnsi = Ansi.ansi().fg(color).bold().a(str).reset().toString();
        int paddingLength = strAnsi.length() - str.length() + padding;
        return StringUtils.center(strAnsi, paddingLength, "*");
    }

    /**
     * Sanitize a fuzzer name by removing a specific suffix.
     *
     * @param currentName The current fuzzer name.
     * @return The sanitized fuzzer name.
     */
    public static String sanitizeFuzzerName(String currentName) {
        return currentName.replace(QUARKUS_PROXY_SUFFIX, "");
    }

    /**
     * Remove specific substrings from a fuzzer name based on a regular expression pattern.
     *
     * @param currentName The current fuzzer name.
     * @return The modified fuzzer name.
     */
    public static String removeTrimSanitize(String currentName) {
        return currentName.replaceAll(REGEX_TO_REMOVE_FROM_FUZZER_NAMES, "");
    }

    /**
     * Get the type of the terminal.
     *
     * @return The terminal type or "unknown" if unable to determine.
     */
    public static String getTerminalType() {
        String terminalType = System.getenv("TERM");
        if (terminalType != null && !terminalType.isEmpty()) {
            return terminalType;
        }

        return "unknown";
    }

    /**
     * Get the user's shell environment variable.
     *
     * @return The shell or "unknown" if the environment variable is not set.
     */
    public static String getShell() {
        return Optional.ofNullable(System.getenv("SHELL")).orElse("unknown");
    }

    /**
     * Render a progress row on the same console row.
     *
     * @param path       The path being processed.
     * @param percentage The completion percentage.
     */
    public static void renderSameRow(String path, double percentage) {
        renderRow("\r", path, ((int) Math.min(percentage, 100d)) + "%");
    }

    /**
     * Render a progress row on the same console row.
     *
     * @param path     The path being processed.
     * @param progress A progress character.
     */
    public static void renderSameRow(String path, String progress) {
        renderRow("\r", path, progress);
    }

    /**
     * Render a progress row on a new console row.
     *
     * @param path       The path being processed.
     * @param percentage The completion percentage.
     */
    public static void renderNewRow(String path, double percentage) {
        renderRow(System.lineSeparator(), path, ((int) Math.min(percentage, 100d)) + "%");
    }

    /**
     * Render a progress row with a specific prefix.
     *
     * @param prefix            The prefix for the progress row.
     * @param path              The path being processed.
     * @param rightTextToRender The text to be written on the right hand side of the screen.
     */
    public static void renderRow(String prefix, String path, String rightTextToRender) {
        String withoutAnsi = ANSI_REMOVE_PATTERN.matcher(path).replaceAll("");
        int dots = Math.max(terminalWidth - withoutAnsi.length() - rightTextToRender.length() - 2, 1);
        String firstPart = path.substring(0, path.indexOf("  "));
        String secondPart = path.substring(path.indexOf("  ") + 1);
        String toPrint = Ansi.ansi().bold().a(prefix + firstPart + " " + ".".repeat(dots) + secondPart + "  " + rightTextToRender).reset().toString();
        System.out.print(toPrint);
    }

    /**
     * Get the number of console columns, subtracting a specified value.
     *
     * @param toSubtract The value to subtract from the total number of columns.
     * @return The adjusted number of columns.
     */
    public static int getConsoleColumns(int toSubtract) {
        return terminalWidth - toSubtract;
    }

    /**
     * Render a header in the console with surrounding equals signs.
     *
     * @param header The header text to be rendered.
     */
    public static void renderHeader(String header) {
        LOGGER.noFormat(" ");
        int equalsNo = (terminalWidth - header.length()) / 2;
        LOGGER.noFormat(Ansi.ansi().bold().a("=".repeat(equalsNo) + header + "=".repeat(equalsNo)).reset().toString());
    }

    /**
     * Prints the given message to the console on the same row and then moves the cursor to a new line.
     *
     * @param message the message
     */
    public static void renderSameRow(String message) {
        int spacesToAdd = getConsoleColumns(message.length());
        System.out.print("\r" + message + " ".repeat(spacesToAdd) + "\n");
    }

    /**
     * Print an empty line to the console.
     */
    public static void emptyLine() {
        LOGGER.noFormat(" ");
    }
}
