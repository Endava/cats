package com.endava.cats.generator.format;

import com.endava.cats.generator.format.impl.InvalidByteFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidDateFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidDateTimeFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidEmailFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidHostnameFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidIPV4FormatGenerator;
import com.endava.cats.generator.format.impl.InvalidIPV6FormatGenerator;
import com.endava.cats.generator.format.impl.NoFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidPasswordFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidURIFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidURLFormatGenerator;
import com.endava.cats.generator.format.impl.InvalidUUIDFormatGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InvalidFormatTest {

    @Test
    void givenASchemaFormat_whenRetrievingTheFormatGenerator_thenTheCorrespondingFormatGeneratorInstanceIsBeingReturned() {
        Assertions.assertThat(InvalidFormat.from("email").getGeneratorStrategy()).isInstanceOf(InvalidEmailFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("byte").getGeneratorStrategy()).isInstanceOf(InvalidByteFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("date").getGeneratorStrategy()).isInstanceOf(InvalidDateFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("date-time").getGeneratorStrategy()).isInstanceOf(InvalidDateTimeFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("email").getGeneratorStrategy()).isInstanceOf(InvalidEmailFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("hostname").getGeneratorStrategy()).isInstanceOf(InvalidHostnameFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("ip").getGeneratorStrategy()).isInstanceOf(InvalidIPV4FormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("ipv4").getGeneratorStrategy()).isInstanceOf(InvalidIPV4FormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("ipv6").getGeneratorStrategy()).isInstanceOf(InvalidIPV6FormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("password").getGeneratorStrategy()).isInstanceOf(InvalidPasswordFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("uri").getGeneratorStrategy()).isInstanceOf(InvalidURIFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("url").getGeneratorStrategy()).isInstanceOf(InvalidURLFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("uuid").getGeneratorStrategy()).isInstanceOf(InvalidUUIDFormatGenerator.class);
        Assertions.assertThat(InvalidFormat.from("any").getGeneratorStrategy()).isInstanceOf(NoFormatGenerator.class);
    }
}
