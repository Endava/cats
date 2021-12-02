package com.endava.cats.args;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.Proxy;

class AuthArgumentsTest {

    @Test
    void shouldReturnEmptyBasicAuth() {
        AuthArguments args = new AuthArguments();
        Assertions.assertThat(args.isBasicAuthSupplied()).isFalse();
    }


    @Test
    void shouldReturnBasicAuthHeader() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "basicAuth", "user:pwd");
        Assertions.assertThat(args.isBasicAuthSupplied()).isTrue();
        Assertions.assertThat(args.getBasicAuthHeader().getName()).isEqualTo("Authorization");
        Assertions.assertThat(args.getBasicAuthHeader().getValue()).isEqualTo("Basic dXNlcjpwd2Q=");

    }

    @Test
    void shouldReturnFalseMutualTls() {
        AuthArguments args = new AuthArguments();
        Assertions.assertThat(args.isMutualTls()).isFalse();
    }

    @Test
    void shouldReturnTrueMutualTls() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "sslKeystore", "keystore.jks");
        Assertions.assertThat(args.isMutualTls()).isTrue();
    }


    @ParameterizedTest
    @CsvSource(value = {"null,0", "localhost,0", "null,8080"}, nullValues = "null")
    void shouldReturnNotIsProxyHost(String host, int port) {
        AuthArguments args = new AuthArguments();

        ReflectionTestUtils.setField(args, "proxyHost", host);
        ReflectionTestUtils.setField(args, "proxyPort", port);

        Assertions.assertThat(args.isProxySupplied()).isFalse();
        Assertions.assertThat(args.getProxy().type()).isEqualTo(Proxy.Type.DIRECT);
    }


    @Test
    void shouldReturnProxy() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "proxyHost", "localhost");
        ReflectionTestUtils.setField(args, "proxyPort", 8080);

        Assertions.assertThat(args.isProxySupplied()).isTrue();
        Assertions.assertThat(args.getProxy().type()).isEqualTo(Proxy.Type.HTTP);
    }
}
