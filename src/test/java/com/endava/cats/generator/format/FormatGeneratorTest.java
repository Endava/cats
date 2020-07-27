package com.endava.cats.generator.format;

import com.endava.cats.generator.format.impl.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FormatGeneratorTest {

    @Test
    void givenASchemaFormat_whenRetrievingTheFormatGenerator_thenTheCorrespondingFormatGeneratorInstanceIsBeingReturned() {
        Assertions.assertThat(FormatGenerator.from("email").getGeneratorStrategy()).isInstanceOf(EmailFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("byte").getGeneratorStrategy()).isInstanceOf(ByteFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("date").getGeneratorStrategy()).isInstanceOf(DateFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("date-time").getGeneratorStrategy()).isInstanceOf(DateTimeFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("email").getGeneratorStrategy()).isInstanceOf(EmailFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("hostname").getGeneratorStrategy()).isInstanceOf(HostnameFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("ip").getGeneratorStrategy()).isInstanceOf(IPV4FormatGenerationStrategy.class);
        Assertions.assertThat(FormatGenerator.from("ipv4").getGeneratorStrategy()).isInstanceOf(IPV4FormatGenerationStrategy.class);
        Assertions.assertThat(FormatGenerator.from("ipv6").getGeneratorStrategy()).isInstanceOf(IPV6FormatGenerationStrategy.class);
        Assertions.assertThat(FormatGenerator.from("password").getGeneratorStrategy()).isInstanceOf(PasswordFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("uri").getGeneratorStrategy()).isInstanceOf(URIFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("url").getGeneratorStrategy()).isInstanceOf(URLFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("uuid").getGeneratorStrategy()).isInstanceOf(UUIDFormatGeneratorStrategy.class);
        Assertions.assertThat(FormatGenerator.from("any").getGeneratorStrategy()).isInstanceOf(NoFormatGenerationStrategy.class);
    }
}
