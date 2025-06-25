package com.endava.cats.openapi.handler.index;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class SpecPositionIndexTest {
    @Test
    void shouldParseYamlAndLocatePointers() throws Exception {
        SpecPositionIndex index = new SpecPositionIndex();
        Path yamlPath = Path.of("src/test/resources/petstore.yml");
        index.parseSpecs(yamlPath);
        Optional<SpecPosition> root = index.locate("");
        assertThat(root).isPresent();

        Optional<SpecPosition> info = index.locate("/info");
        assertThat(info).isPresent();

        Optional<SpecPosition> paths = index.locate("/paths");
        assertThat(paths).isPresent();

        assertThat(index.locate("/notfound")).isEmpty();
    }

    @Test
    void shouldParseJsonAndLocatePointers() throws Exception {
        SpecPositionIndex index = new SpecPositionIndex();
        Path jsonPath = Path.of("src/test/resources/issue117.json");
        index.parseSpecs(jsonPath);

        Optional<SpecPosition> root = index.locate("");
        assertThat(root).isPresent();

        Optional<SpecPosition> info = index.locate("/info");
        assertThat(info).isPresent();

        assertThat(index.locate("/notfound")).isEmpty();
    }

    @Test
    void shouldHandleEmptyOrInvalidFilesGracefully() {
        SpecPositionIndex index = new SpecPositionIndex();
        Path invalidPath = Path.of("src/test/resources/empty.yml");
        assertThatThrownBy(() -> index.parseSpecs(invalidPath)).isInstanceOf(Exception.class);
    }
}
