package com.endava.cats.factory;

import com.endava.cats.model.NoMediaType;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NoMediaTypeTest {


    @Test
    void shouldReturnDefaultSchema() {
        Assertions.assertThat(new NoMediaType().getSchema().get$ref()).isEqualTo("#/components/schemas/empty_body");
    }
}
