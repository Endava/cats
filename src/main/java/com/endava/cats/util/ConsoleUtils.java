package com.endava.cats.util;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;

public final class ConsoleUtils {

    public static String centerWithAnsiColor(String str, int padding, Ansi.Color color) {
        String strAnsi = Ansi.ansi().fg(color).bold().a(str).reset().fgCyan().toString();
        int paddingLength = strAnsi.length() - str.length() + padding;
        return StringUtils.center(strAnsi, paddingLength, "*");
    }
}
