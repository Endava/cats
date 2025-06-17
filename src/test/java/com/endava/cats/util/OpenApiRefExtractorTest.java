package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@QuarkusTest
class OpenApiRefExtractorTest {

    @TempDir
    Path tempDir;

    private Path mainFile;
    private Path schemaFile;
    private Path nestedFile;

    @BeforeEach
    void setUp() throws IOException {
        if (tempDir == null) {
            tempDir = Files.createTempDirectory("test-temp-dir");
        }

        mainFile = tempDir.resolve("main.yaml");
        schemaFile = tempDir.resolve("schema.yaml");
        nestedFile = tempDir.resolve("nested.yaml");
    }

    @Test
    void shouldExtractRefsFromOpenAPIBasicFileReferences() throws IOException {
        Files.write(mainFile,
                ("""
                        openapi: 3.0.0
                        $ref: 'schema.yaml'
                        $ref: "nested.yaml#/NestedSchema"
                        """).getBytes());

        Files.write(schemaFile,
                ("""
                        Schema:
                          type: object
                        """).getBytes());

        Files.write(nestedFile,
                ("""
                        NestedSchema:
                          type: string
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(2);
        assertThat(result).contains("schema", "NestedSchema");
    }

    @Test
    void shouldExtractRefsFromOpenAPILocalPointers() throws IOException {
        Files.write(mainFile,
                ("""
                        openapi: 3.0.0
                        $ref: '#/components/schemas/User'
                        $ref: '#/definitions/Pet'
                        $ref: '#/SimpleSchema'
                        $ref: '#/example/ReauthorisationBody'
                        $ref: '#/component/response/InvalidRequestGenericResponse'
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(5);
        assertThat(result).contains("User", "Pet", "SimpleSchema", "ReauthorisationBody", "InvalidRequestGenericResponse");
    }

    @Test
    void shouldExtractRefsFromOpenAPIMixedReferences() throws IOException {
        Files.write(mainFile,
                ("""
                        openapi: 3.0.0
                        $ref: 'external.yaml'
                        $ref: '#/components/schemas/LocalSchema'
                        $ref: "models/user.yaml#/UserModel"
                        """).getBytes());

        Path externalFile = tempDir.resolve("external.yaml");
        Files.write(externalFile,
                ("""
                        ExternalSchema:
                          $ref: '#/InternalRef'
                        """).getBytes());

        Path modelsDir = tempDir.resolve("models");
        Files.createDirectories(modelsDir);
        Path userFile = modelsDir.resolve("user.yaml");
        Files.write(userFile,
                ("""
                        UserModel:
                          type: object
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(4);
        assertThat(result).contains("external", "LocalSchema", "UserModel", "InternalRef");
    }

    @Test
    void shouldExtractRefsFromOpenAPICyclicReferences() throws IOException {
        Files.write(mainFile,
                "$ref: 'cyclic1.yaml'\n".getBytes());

        Path cyclic1 = tempDir.resolve("cyclic1.yaml");
        Path cyclic2 = tempDir.resolve("cyclic2.yaml");

        Files.write(cyclic1,
                "$ref: 'cyclic2.yaml'\n".getBytes());

        Files.write(cyclic2,
                "$ref: 'cyclic1.yaml'\n".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(2);
        assertThat(result).contains("cyclic1", "cyclic2");
    }

    @Test
    void shouldExtractRefsFromOpenAPIDifferentQuoteStyles() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: 'single-quote.yaml'
                        $ref: "double-quote.yaml"
                        $ref: no-quote.yaml
                        $ref:    spaced.yaml  \s
                        """).getBytes());

        Files.write(tempDir.resolve("single-quote.yaml"), "content".getBytes());
        Files.write(tempDir.resolve("double-quote.yaml"), "content".getBytes());
        Files.write(tempDir.resolve("no-quote.yaml"), "content".getBytes());
        Files.write(tempDir.resolve("spaced.yaml"), "content".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(4);
        assertThat(result).contains("single-quote", "double-quote", "no-quote", "spaced");
    }

    @Test
    void shouldExtractRefsFromOpenAPIVariousFileExtensions() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: 'schema.yaml'
                        $ref: 'config.yml'
                        $ref: 'data.json'
                        $ref: 'ignored.txt'
                        """).getBytes());

        Files.write(tempDir.resolve("schema.yaml"), "content".getBytes());
        Files.write(tempDir.resolve("config.yml"), "content".getBytes());
        Files.write(tempDir.resolve("data.json"), "content".getBytes());
        Files.write(tempDir.resolve("ignored.txt"), "content".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(3);
        assertThat(result).contains("schema", "config", "data");
        assertThat(result).doesNotContain("ignored");
    }

    @Test
    void shouldExtractRefsFromOpenAPINonExistentFile() {
        Path nonExistentFile = tempDir.resolve("non-existent.yaml");

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(nonExistentFile.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExtractRefsFromOpenAPINonExistentReferencedFile() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: 'non-existent.yaml'
                        $ref: '#/LocalSchema'
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(2);
        assertThat(result).contains("non-existent", "LocalSchema");
    }

    @Test
    void shouldExtractRefsFromOpenAPIEmptyFile() throws IOException {
        Files.write(mainFile, "".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExtractRefsFromOpenAPINoReferences() throws IOException {
        Files.write(mainFile,
                ("""
                        openapi: 3.0.0
                        info:
                          title: Test API
                          version: 1.0.0
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExtractRefsFromOpenAPIComplexJsonPointers() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: '#/components/schemas/User/properties/address'
                        $ref: '#/paths/users/{id}/get/responses/200'
                        $ref: '#/definitions/Pet/allOf/0'
                        $ref: '#/single'
                        $ref: '#/'
                        $ref: '#/empty//segment'
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(5);
        assertThat(result).contains("address", "200", "0", "single", "segment");
    }

    @Test
    void shouldExtractRefsFromOpenAPINestedDirectories() throws IOException {
        Path subDir = tempDir.resolve("models").resolve("common");
        Files.createDirectories(subDir);

        Files.write(mainFile,
                "$ref: 'models/common/error.yaml#/ErrorResponse'\n".getBytes());

        Path errorFile = subDir.resolve("error.yaml");
        Files.write(errorFile,
                ("""
                        ErrorResponse:
                          $ref: '#/BaseError'
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(2);
        assertThat(result).contains("ErrorResponse", "BaseError");
    }

    @Test
    void shouldExtractRefsFromOpenAPIRelativePathResolution() throws IOException {
        Path subDir = tempDir.resolve("schemas");
        Files.createDirectories(subDir);

        Path schemaInSubDir = subDir.resolve("main-schema.yaml");
        Files.write(schemaInSubDir,
                ("""
                        $ref: '../shared.yaml'
                        $ref: 'local.yaml'
                        """).getBytes());

        Files.write(tempDir.resolve("shared.yaml"), "content".getBytes());
        Files.write(subDir.resolve("local.yaml"), "content".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(schemaInSubDir.toString());

        assertThat(result).hasSize(2);
        assertThat(result).contains("shared", "local");
    }

    @Test
    void shouldHandleIOExceptionWhenExtractingRefsFromOpenAPI() throws IOException {
        Files.writeString(mainFile, "$ref: 'test.yaml'\n");

        Path problematicFile = tempDir.resolve("test.yaml");
        Files.writeString(problematicFile, "content");

        File file = problematicFile.toFile();
        file.setReadable(false, false);

        try {
            Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

            assertThat(result).hasSize(1);
            assertThat(result).contains("test");
        } finally {
            // Reset permissions so the file can be deleted
            file.setReadable(true, false);
        }
    }

    @Test
    void shouldHandleInvalidPathWhenExtractingRefsFromOpenAPI() {
        String invalidPath = "\0invalid\0path";

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(invalidPath);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleDirectoryInsteadOfFileWhenExtractingRefsFromOpenAPI() throws IOException {
        Path directory = tempDir.resolve("directory");
        Files.createDirectories(directory);

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(directory.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleCaseInsensitiveFileExtensionsWhenExtractingRefsFromOpenAPI() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: 'schema.YAML'
                        $ref: 'config.YML'
                        $ref: 'data.JSON'
                        """).getBytes());

        Files.write(tempDir.resolve("schema.YAML"), "content".getBytes());
        Files.write(tempDir.resolve("config.YML"), "content".getBytes());
        Files.write(tempDir.resolve("data.JSON"), "content".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(3);
        assertThat(result).contains("schema", "config", "data");
    }

    @Test
    void shouldTestRefInfoToString() throws IOException {
        Files.write(mainFile,
                ("$ref: 'test.yaml#/Schema'\n").getBytes());

        assertThatCode(() -> OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString())).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleWhitespaceInReferencesWhenExtractingRefsFromOpenAPI() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref:   '  spaced-file.yaml  ' \s
                        $ref: '#/spaced/pointer  '
                        """).getBytes());

        Files.write(tempDir.resolve("spaced-file.yaml"), "content".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(2);
        assertThat(result).contains("spaced-file", "pointer");
    }

    @Test
    void shouldHandleSpecialCharactersInSchemaNamesWhenExtractingRefsFromOpenAPI() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: '#/components/schemas/User_Name'
                        $ref: '#/components/schemas/User-Name'
                        $ref: '#/components/schemas/User.Name'
                        $ref: '#/components/schemas/User123'
                        """).getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).hasSize(4);
        assertThat(result).contains("User_Name", "User-Name", "User.Name", "User123");
    }

    @Test
    void shouldHandleEmptyJsonPointerWhenExtractingRefsFromOpenAPI() throws IOException {
        Files.write(mainFile,
                ("""
                        $ref: '#/'
                        $ref: '#'
                        """).getBytes());

        Files.write(tempDir.resolve("file.yaml"), "content".getBytes());

        Set<String> result = OpenApiRefExtractor.extractRefsFromOpenAPI(mainFile.toString());

        assertThat(result).isEmpty();
    }
}
