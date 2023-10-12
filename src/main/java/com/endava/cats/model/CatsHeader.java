package com.endava.cats.model;

import com.endava.cats.generator.simple.StringGenerator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * This class represents the HTTP headers from the OpenAPI contract and use them as the data model across all layers.
 */
@Builder
@AllArgsConstructor
@Getter
@EqualsAndHashCode(of = "name")
public class CatsHeader {

    private final boolean required;
    private final String name;
    private String value;

    private CatsHeader(Parameter param) {
        this.name = param.getName();
        this.required = param.getRequired() != null && param.getRequired();
        this.value = this.generateValue(param.getSchema());
    }

    /**
     * Creates a new CatsHeader from a OpenAPI Parameter object.
     *
     * @param param the OpenAPI Parameter object
     * @return a CatsHeader object created based on the supplied Parameter
     */
    public static CatsHeader fromHeaderParameter(Parameter param) {
        return new CatsHeader(param);
    }

    /**
     * Sets the header's value.
     *
     * @param value the value to be set
     */
    public void withValue(String value) {
        this.value = value;
    }

    /**
     * Truncates the value if is longer than 50 characters.
     *
     * @return a truncated value of the header
     */
    public String getTruncatedValue() {
        if (this.value != null && this.value.length() > 50) {
            return this.value.substring(0, 20) + "...[Total length:" + this.value.length() + "]";
        }

        return this.value;
    }


    /**
     * Creates a copy of the current header.
     *
     * @return a copy of the current header
     */
    public CatsHeader copy() {
        return CatsHeader.builder().name(this.name).required(this.required).value(this.value).build();
    }

    private String generateValue(Schema schema) {
        if (schema.getExample() != null) {
            return schema.getExample().toString();
        }

        if ("uuid".equalsIgnoreCase(schema.getFormat())) {
            return UUID.randomUUID().toString();
        }
        if ("date-time".equalsIgnoreCase(schema.getFormat())) {
            return OffsetDateTime.now().toString();
        }

        if ("string".equalsIgnoreCase(schema.getType())) {
            String pattern = schema.getPattern() != null ? schema.getPattern() : StringGenerator.ALPHANUMERIC_PLUS;
            int minLength = schema.getMinLength() != null ? schema.getMinLength() : 0;
            int maxLength = schema.getMaxLength() != null ? schema.getMaxLength() : 30;
            if (minLength == 0) {
                minLength = maxLength / 2;
            }

            return StringGenerator.generate(pattern, minLength, maxLength);
        }

        if (schema.getPattern() != null) {
            return StringGenerator.generate(schema.getPattern(), 1, 200);
        }

        return this.name;
    }

    @Override
    public String toString() {
        return "{" + "required=" + required + ", name='" + name + '\'' + '}';
    }
}
