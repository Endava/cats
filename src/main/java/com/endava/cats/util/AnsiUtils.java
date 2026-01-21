package com.endava.cats.util;

import org.fusesource.jansi.Ansi;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Utility methods for ANSI-formatted console output.
 * Provides consistent styling across the application.
 */
public abstract class AnsiUtils {

    private AnsiUtils() {
        // Utility class
    }

    /**
     * Formats text in green color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String green(Object value) {
        return ansi().fgGreen().a(value).reset().toString();
    }

    /**
     * Formats text in yellow color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String yellow(Object value) {
        return ansi().fgYellow().a(value).reset().toString();
    }

    /**
     * Formats text in blue color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String blue(Object value) {
        return ansi().fg(Ansi.Color.BLUE).a(value).reset().toString();
    }

    /**
     * Formats text in red color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String red(Object value) {
        return ansi().fg(Ansi.Color.RED).a(value).reset().toString();
    }

    /**
     * Formats text in bold.
     *
     * @param text the text to format
     * @return ANSI-formatted string
     */
    public static String bold(String text) {
        return ansi().bold().a(text).reset().toString();
    }

    /**
     * Formats text in bold blue color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String boldBlue(Object value) {
        return ansi().bold().fg(Ansi.Color.BLUE).a(value).reset().toString();
    }

    /**
     * Formats text in bold bright blue color.
     *
     * @param text the text to format
     * @return ANSI-formatted string
     */
    public static String boldBrightBlue(String text) {
        return ansi().bold().fgBrightBlue().a(text).reset().toString();
    }

    /**
     * Formats text in bold bright yellow color.
     *
     * @param text the text to format
     * @return ANSI-formatted string
     */
    public static String boldYellow(String text) {
        return ansi().bold().fgBrightYellow().a(text).reset().toString();
    }

    /**
     * Formats text in bold green color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String boldGreen(Object value) {
        return ansi().bold().fgGreen().a(value).reset().toString();
    }

    /**
     * Formats text in bold red color.
     *
     * @param value the value to format
     * @return ANSI-formatted string
     */
    public static String boldRed(Object value) {
        return ansi().bold().fgRed().a(value).reset().toString();
    }

    /**
     * Formats text in bold with specific Ansi.Color.
     *
     * @param value the value to format
     * @param color the Ansi color
     * @return ANSI-formatted string
     */
    public static String boldColor(Object value, Ansi.Color color) {
        return ansi().bold().fg(color).a(value).reset().toString();
    }

    /**
     * Formats text in specific Ansi.Color.
     *
     * @param value the value to format
     * @param color the Ansi color
     * @return ANSI-formatted string
     */
    public static String color(Object value, Ansi.Color color) {
        return ansi().fg(color).a(value).reset().toString();
    }
}
