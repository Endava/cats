package com.endava.cats.util;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;

public abstract class ConsoleUtils {

    private static final String QUARKUS_PROXY_SUFFIX = "_Subclass";
    private static final String REGEX_TO_REMOVE_FROM_FUZZER_NAMES = "TrimValidate|ValidateTrim|SanitizeValidate|ValidateSanitize|Fuzzer|Of";

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
}
