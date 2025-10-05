package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class OpenApiServerExtractorTest {

    @Test
    void shouldReturnEmptyListWhenServersIsNull() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        when(openAPI.getServers()).thenReturn(null);

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenServersIsEmpty() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        when(openAPI.getServers()).thenReturn(Collections.emptyList());

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnServerUrlWhenNoVariables() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        when(server.getUrl()).thenReturn("https://api.example.com");
        when(server.getVariables()).thenReturn(null);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(1)
                .containsExactly("https://api.example.com");
    }

    @Test
    void shouldReturnServerUrlWhenEmptyVariables() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = mock(ServerVariables.class);
        when(server.getUrl()).thenReturn("https://api.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(variables.isEmpty()).thenReturn(true);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(1)
                .containsExactly("https://api.example.com");
    }

    @Test
    void shouldExpandServerUrlWithSingleVariableDefaultOnly() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();
        ServerVariable envVariable = new ServerVariable();
        envVariable.setDefault("prod");
        variables.put("environment", envVariable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(1)
                .containsExactly("https://prod.example.com");
    }

    @Test
    void shouldExpandServerUrlWithSingleVariableEnumValues() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();
        ServerVariable envVariable = new ServerVariable();
        envVariable.setDefault("prod");
        envVariable.setEnum(Arrays.asList("dev", "staging", "prod"));
        variables.put("environment", envVariable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        "https://prod.example.com",
                        "https://dev.example.com",
                        "https://staging.example.com"
                );
    }

    @Test
    void shouldExpandServerUrlWithMultipleVariables() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();

        ServerVariable envVariable = new ServerVariable();
        envVariable.setDefault("prod");
        envVariable.setEnum(Arrays.asList("dev", "prod"));
        variables.put("environment", envVariable);

        ServerVariable versionVariable = new ServerVariable();
        versionVariable.setDefault("v1");
        versionVariable.setEnum(Arrays.asList("v1", "v2"));
        variables.put("version", versionVariable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com/{version}");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(4)
                .containsExactlyInAnyOrder(
                        "https://prod.example.com/v1",
                        "https://prod.example.com/v2",
                        "https://dev.example.com/v1",
                        "https://dev.example.com/v2"
                );
    }

    @Test
    void shouldHandleMultipleServers() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);

        Server server1 = mock(Server.class);
        when(server1.getUrl()).thenReturn("https://api1.example.com");
        when(server1.getVariables()).thenReturn(null);

        Server server2 = mock(Server.class);
        when(server2.getUrl()).thenReturn("https://api2.example.com");
        when(server2.getVariables()).thenReturn(null);

        when(openAPI.getServers()).thenReturn(Arrays.asList(server1, server2));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        "https://api1.example.com",
                        "https://api2.example.com"
                );
    }

    @Test
    void shouldHandleVariableWithNullDefaultAndNoEnum() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();
        ServerVariable variable = new ServerVariable();
        // variable has null default and null enum
        variables.put("environment", variable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result).containsExactly("https://{environment}.example.com");
    }

    @Test
    void shouldHandleVariableWithNullDefaultAndEmptyEnum() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();
        ServerVariable variable = new ServerVariable();
        variable.setEnum(Collections.emptyList());
        variables.put("environment", variable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result).containsExactly("https://{environment}.example.com");
    }

    @Test
    void shouldHandleDuplicateValuesInEnumAndDefault() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();
        ServerVariable envVariable = new ServerVariable();
        envVariable.setDefault("prod");
        envVariable.setEnum(Arrays.asList("dev", "prod", "staging"));
        variables.put("environment", envVariable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(3)
                .containsExactlyInAnyOrder(
                        "https://prod.example.com",
                        "https://dev.example.com",
                        "https://staging.example.com"
                );
    }

    @Test
    void shouldHandleEnumValuesOnlyWithoutDefault() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();
        ServerVariable envVariable = new ServerVariable();
        envVariable.setEnum(Arrays.asList("dev", "staging"));
        variables.put("environment", envVariable);

        when(server.getUrl()).thenReturn("https://{environment}.example.com");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(2)
                .containsExactlyInAnyOrder(
                        "https://dev.example.com",
                        "https://staging.example.com"
                );
    }

    @Test
    void shouldHandleComplexUrlTemplateWithMultipleVariableOccurrences() {
        // Given
        OpenAPI openAPI = mock(OpenAPI.class);
        Server server = mock(Server.class);
        ServerVariables variables = new ServerVariables();

        ServerVariable envVariable = new ServerVariable();
        envVariable.setDefault("prod");
        variables.put("env", envVariable);

        ServerVariable portVariable = new ServerVariable();
        portVariable.setDefault("8080");
        variables.put("port", portVariable);

        when(server.getUrl()).thenReturn("https://{env}.example.com:{port}/api/{env}");
        when(server.getVariables()).thenReturn(variables);
        when(openAPI.getServers()).thenReturn(Collections.singletonList(server));

        // When
        List<String> result = OpenApiServerExtractor.getServerUrls(openAPI);

        // Then
        assertThat(result)
                .hasSize(1)
                .containsExactly("https://prod.example.com:8080/api/prod");
    }
}