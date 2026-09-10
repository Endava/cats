package com.endava.cats;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@QuarkusTest
class NativeResourcesTest {
    @Test
    void shouldIncludeAllReportTemplatesInNativeExecutables() throws IOException {
        Properties properties = new Properties();
        try (InputStream applicationProperties = Files.newInputStream(
                Path.of("src/main/resources/application.properties"))) {
            properties.load(applicationProperties);
        }

        Assertions.assertThat(properties.getProperty("quarkus.native.resources.includes"))
                .contains("*.mustache");
    }
}
