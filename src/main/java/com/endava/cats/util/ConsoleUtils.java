package com.endava.cats.util;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;

public final class ConsoleUtils {

    private ConsoleUtils() {
        //ntd
    }

    public static String centerWithAnsiColor(String str, int padding, Ansi.Color color) {
        String strAnsi = Ansi.ansi().fg(color).bold().a(str).reset().toString();
        int paddingLength = strAnsi.length() - str.length() + padding;
        return StringUtils.center(strAnsi, paddingLength, "*");
    }

    public static String sanitizeFuzzerName(String currentName) {
        return currentName.replace("_Subclass", "");
    }

    public static String removeTrimSanitize(String currentName) {
        return currentName.replaceAll("TrimValidate|ValidateTrim|SanitizeValidate|ValidateSanitize", "");
    }
}
