package com.endava.cats.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class ConsoleUtils {

    private static final String QUARKUS_PROXY_SUFFIX = "_Subclass";
    private static final String REGEX_TO_REMOVE_FROM_FUZZER_NAMES = "TrimValidate|ValidateTrim|SanitizeValidate|ValidateSanitize|Fuzzer";

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getConsoleLogger();

    private static final Pattern ANSI_REMOVE_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    private ConsoleUtils() {
        //ntd
    }

    public static String centerWithAnsiColor(String str, int padding, Ansi.Color color) {
        String strAnsi = Ansi.ansi().fg(color).bold().a(str).reset().toString();
        int paddingLength = strAnsi.length() - str.length() + padding;
        return StringUtils.center(strAnsi, paddingLength, "*");
    }

    public static String sanitizeFuzzerName(String currentName) {
        return currentName.replace(QUARKUS_PROXY_SUFFIX, "");
    }

    public static String removeTrimSanitize(String currentName) {
        return currentName.replaceAll(REGEX_TO_REMOVE_FROM_FUZZER_NAMES, "");
    }

    public static String getTerminalType() {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            return terminal.getType();

        } catch (IOException e) {
            LOGGER.debug("Unable to determine terminal type.");
        }
        return "unknown";
    }

    public static String getShell() {
        return Optional.ofNullable(System.getenv("SHELL")).orElse("unknown");
    }

    public static int getTerminalWidth(int defaultWidth) {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            return terminal.getSize().getColumns() > 0 ? terminal.getSize().getColumns() : defaultWidth;
        } catch (IOException e) {
            LOGGER.debug("Unable to determine terminal size. Using {}", defaultWidth);
        }

        return defaultWidth;
    }


    public static void renderSameRow(String path, double percentage) {
        renderRow("\r", path, Math.min(percentage, 100d));
    }

    public static void renderNewRow(String path, double percentage) {
        renderRow(System.lineSeparator(), path, Math.min(percentage, 100d));
    }

    public static void renderRow(String prefix, String path, Double percentage) {
        String withoutAnsi = ANSI_REMOVE_PATTERN.matcher(path).replaceAll("");
        int consoleWidth = getTerminalWidth(80);
        int dots = Math.max(consoleWidth - withoutAnsi.length() - String.valueOf(percentage.intValue()).length() - 2, 1);
        String firstPart = path.substring(0, path.indexOf("  "));
        String secondPart = path.substring(path.indexOf("  ") + 1);
        System.out.print(Ansi.ansi().bold().a(prefix + firstPart + ".".repeat(dots) + secondPart + "  " + percentage.intValue() + "%").reset().toString());
    }

    public static int getConsoleColumns(int toSubtract) {
        int columns = getTerminalWidth(120);

        return columns - toSubtract;
    }

    public static void renderHeader(String header) {
        LOGGER.noFormat(" ");
        int toAdd = header.length() % 2;
        int equalsNo = (getTerminalWidth(80) - header.length()) / 2;
        LOGGER.noFormat(Ansi.ansi().bold().a("=".repeat(equalsNo) + header + "=".repeat(equalsNo + toAdd)).reset().toString());
    }

    public static void emptyLine() {
        LOGGER.noFormat(" ");
    }
}
