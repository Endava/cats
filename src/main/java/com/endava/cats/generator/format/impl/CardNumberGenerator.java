package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * A generator class implementing various interfaces for generating valid and invalid card number data formats.
 */
@Singleton
public class CardNumberGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {

    static final List<String> CARDS = List.of(
            "4485785156913636", "4716210684476791", "4929532217247180", "4929460887451637", "4929638520597888",
            "5259272637080971", "5411382200125346", "5371612728016173", "5463084305505847", "5532093434659042",
            "6011334474724389", "6011315558568180", "6011727787327750", "6011659001329850", "6011729202913511",
            "371277972520881", "340706417617348", "376559356956996");

    @Override
    public Object generate(Schema<?> schema) {
        return CARDS.get(CatsUtil.random().nextInt(CARDS.size()));
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "cardnumber".equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).endsWith("cardnumber");
    }

    @Override
    public String getAlmostValidValue() {
        return "2222420000001112";
    }

    @Override
    public String getTotallyWrongValue() {
        return "4444444444444444";
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("cardNumber", "card-number", "card_number");
    }
}
