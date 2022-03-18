package com.endava.cats.model.util;

import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.model.FuzzingStrategy;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class PayloadUtils {

    public static final String ZALGO_TEXT = "c̷̨̛̥̬͉̘̬̻̩͕͚̦̺̻͓̳͇̲̭̝̙̟̈́̉̐͂͒̆͂̿͌̑͐̌̇̈́̾̉̆̀̅̓͛͋̈̄͊̈̄̎̃̒͂̓̊̌̎̌̃́̅͊̏͘͘͘̕̕͘͠͝a̶͖̐͆͑́͆̓͗͆̏̑̈́̾͛̎̂̒̄̏̍͌͛̀́̄̓̍̐͂̀́̈́̂͐̕̕̕̚͘͠͝͠t̵̨̢̨͙̪̼͚͖̲̻̞̦̤̲̖͚̟̯͔̬̜̬͖̺͎̼̬̞̱̳͚͔͎̩̩̩̲̗̩̊̽̈́̔̀̍͒̓̂͐̾̆̐̒̄͂͒̽̾̔͊̒̀͗̿̈́͆͆̂͆̈́̋̏͊̉͌̒̏̓̑͛̉͘͜͜͜͝͝͠͠ş̶̨̢̧̛̛̱̜͈͓̗͍͈̰̱͔̥͙̺̤̠̩̮́̋̒͗̌̔̄̓̓͐̇̾̀́̓̆͗̂̐͊̓̓́̀͌̐̒̆̏̐͐̌̀́̈́̑̄͛̔̌͘̚̕͜͠ͅ ̸̡̡̧̡̨̧̧̯͚̥̙͉̲̠͚̼̤̹̹̳͕̙͔̺̥̼̙̙͚̳̰͕̤͕̀͒̈́̆̆̅̀̑̋̾͒̈́̅͌̀͑͋͋̎͂͂̄̑̆͒̃̓́̂̈́̑̄͝į̴̬͙͕̤͎͇̹̮̯̞̦̱̠̤̖̣̆͊̀̀̓͛͗͛̈͂̌̉̊͐̆̈̉͂͌̊́̉̋͘̚̚͜͝͝ș̷̡̛̛̮̲̥͙̞̤̘̉͛͗̿͂̏͛̾̂̂̄͗́̈́́̅̄̇̈́͗̀̂̈̉̐͑̏̒̈́͗̆͆̆̆͐̀͋̋͌̚̚͝͝ ̴̧̢̛̥̼̘̬̮͚͙̙̳͇̣̬̓̽̃̇̅͆͌̓̒̾͌̒͋͆́̓͛̔͛͒̉̔̏̔̂͐͛͗̾̎͂̏̋͘̚͝͝ç̵̡̧̛̛̟̩̲̲̲̫̺͎͎̘͎̘̱̭̬̗̎̾̏̂̏͑͊̾̎̂̉̊̉̐̓̾͒̓̓̒̔̽̄́͋̀̈́́̓̏͑͗̂̂̈́̒̚͘̕͘͝͠͝ͅͅͅơ̶̛̩̫̊̿̇͊͆́̅̈̽̆̓͛̌͐̍̀͒̐͑̀̎̀̀̉̑͛̔͋́̀͂̈̐̾̊̓͑̔͐̚̕͝͝͝͝͠ô̷̡̧̧̨̢̱͈̠̬̤̪̖̘͍̥̝͍̺̠̮̫̺̳͚͈͕̞̯̳̩̗̜̺̜̠͔̖̥͆͛͑́̆͛͐̓̒͊̊͑̽̄̐͊̓̃̚͜͜͝l";
    private static final List<String> spacesHeaders = Arrays.asList(" ", "\u0009", "\r");
    private static final List<String> whitespacesHeaders = Arrays.asList(
            "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006", "\u2007", "\u2008", "\u2009",
            "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\u00A0");
    private static final List<String> whitespacesFields = Arrays.asList(
            " ", "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006",
            "\u2007", "\u2008", "\u2009", "\u200A", "\u2028", "\u2029", "\u202F", "\u205F", "\u3000", "\u00A0");
    private static final List<String> controlCharsHeaders = Arrays.asList(
            "\r\n", "\u0000", "\u0007", "\u0008", "\n", "\u000B", "\u000C", "\r", "\u200B", "\u200C", "\u200D", "\u200E",
            "\u200F", "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", "\u2060", "\u2061", "\u2062", "\u2063", "\u2064", "\u206D", "\u0015",
            "\u0016", "\u0017", "\u0018", "\u0019", "\u001A", "\u001B", "\u001C", "\u001D", "\u001E", "\u001F", "\u007F", "\u0080", "\u0081",
            "\u0082", "\u0083", "\u0085", "\u0086", "\u0087", "\u0088", "\u008A", "\u008B", "\u008C", "\u008D", "\u0090", "\u0091", "\u0093",
            "\u0094", "\u0095", "\u0096", "\u0097", "\u0098", "\u0099", "\u009A", "\u009B", "\u009C", "\u009D", "\u009E", "\u009F", "\uFEFF", "\uFFFE", "\u00AD");
    private static final List<String> controlCharsFields = Arrays.asList(
            "\r\n", "\u0007", "\u0008", "\u0009", "\n", "\u000B", "\u000C", "\r", "\u200B", "\u200C", "\u200D", "\u200E",
            "\u200F", "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", "\u2060", "\u2061", "\u2062", "\u2063", "\u2064", "\u206D",
            "\u0015", "\u0016", "\u0017", "\u0018", "\u0019", "\u001A", "\u001B", "\u001C", "\u001D", "\u001E", "\u001F", "\u007F",
            "\u0080", "\u0081", "\u0082", "\u0083", "\u0085", "\u0086", "\u0087", "\u0088", "\u008A", "\u008B", "\u008C", "\u008D",
            "\u0090", "\u0091", "\u0093", "\u0094", "\u0095", "\u0096", "\u0097", "\u0098", "\u0099", "\u009A", "\u009B", "\u009C",
            "\u009D", "\u009E", "\u009F", "\uFEFF", "\uFFFE", "\u00AD");
    private static final List<String> singleCodePointEmojis = Arrays.asList("\uD83E\uDD76", "\uD83D\uDC80", "\uD83D\uDC7B", "\uD83D\uDC7E");
    private static final List<String> multiCodePointEmojis = Arrays.asList("\uD83D\uDC69\uD83C\uDFFE", "\uD83D\uDC68\u200D\uD83C\uDFED️", "\uD83D\uDC69\u200D\uD83D\uDE80");

    private static final List<String> abugidasChars = List.of("జ్ఞ\u200Cా", "স্র\u200Cু");

    private PayloadUtils() {
        //ntd
    }

    public static List<String> getAbugidasChars() {
        return abugidasChars;
    }

    public static List<String> getControlCharsFields() {
        return controlCharsFields;
    }

    public static List<String> getControlCharsHeaders() {
        return controlCharsHeaders;
    }

    public static List<String> getSeparatorsFields() {
        return whitespacesFields;
    }

    public static List<String> getSeparatorsHeaders() {
        return whitespacesHeaders;
    }

    public static List<String> getSpacesHeaders() {
        return spacesHeaders;
    }

    public static List<String> getSingleCodePointEmojis() {
        return singleCodePointEmojis;
    }

    public static List<String> getMultiCodePointEmojis() {
        return multiCodePointEmojis;
    }

    public static String markLargeString(String input) {
        return "ca" + input + "ts";
    }

    public static List<FuzzingStrategy> getLargeValuesStrategy(int largeStringsSize) {
        String generatedValue = StringGenerator.generateRandomUnicode();
        int payloadSize = largeStringsSize / generatedValue.length();
        if (payloadSize == 0) {
            return Collections.singletonList(FuzzingStrategy.replace().withData(markLargeString(generatedValue.substring(0, largeStringsSize))));
        }
        return Collections.singletonList(FuzzingStrategy.replace().withData(markLargeString(StringUtils.repeat(generatedValue, payloadSize + 1))));
    }

}
