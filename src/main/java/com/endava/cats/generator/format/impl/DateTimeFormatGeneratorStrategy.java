package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.FormatGeneratorStrategy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeFormatGeneratorStrategy implements FormatGeneratorStrategy {

    @Override
    public String getAlmostValidValue() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").format(LocalDateTime.now());
    }

    @Override
    public String getTotallyWrongValue() {
        return "1000-07-21T17:32:28Z";
    }
}
