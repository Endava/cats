package com.endava.cats.args;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.Proxy;

class AuthArgumentsTest {

    @Test
    void shouldReturnEmptyBasicAuth() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "basicAuth", "empty");
        args.init();
        Assertions.assertThat(args.isBasicAuthSupplied()).isFalse();
    }


    @Test
    void shouldReturnBasicAuthHeader() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "basicAuth", "user:pwd");
        args.init();
        Assertions.assertThat(args.isBasicAuthSupplied()).isTrue();
        Assertions.assertThat(args.getBasicAuthHeader().getName()).isEqualTo("Authorization");
        Assertions.assertThat(args.getBasicAuthHeader().getValue()).isEqualTo("Basic dXNlcjpwd2Q=");

    }

    @Test
    void shouldReturnFalseMutualTls() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "sslKeystore", "empty");
        args.init();
        Assertions.assertThat(args.isMutualTls()).isFalse();
    }

    @Test
    void shouldReturnTrueMutualTls() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "sslKeystore", "keystore.jks");
        args.init();
        Assertions.assertThat(args.isMutualTls()).isTrue();
    }

    @Test
    void shouldReturnEmptyProxy() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "proxyHost", "empty");
        args.init();
        Assertions.assertThat(args.isProxySupplied()).isFalse();
        Assertions.assertThat(args.getProxy().type()).isEqualTo(Proxy.Type.DIRECT);
    }


    @Test
    void shouldReturnProxy() {
        AuthArguments args = new AuthArguments();
        ReflectionTestUtils.setField(args, "proxyHost", "localhost");
        args.init();
        Assertions.assertThat(args.isProxySupplied()).isTrue();
        Assertions.assertThat(args.getProxy().type()).isEqualTo(Proxy.Type.HTTP);
    }
}
