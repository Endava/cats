package com.endava.cats.args;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApiArgumentsTest {

    @Test
    void shouldNotReturnRemoteContractWhenNull() {
        ApiArguments apiArguments = new ApiArguments();
        Assertions.assertThat(apiArguments.isRemoteContract()).isFalse();
    }

    @Test
    void shouldNotReturnRemoteContractWhenLocal() {
        ApiArguments apiArguments = new ApiArguments();
        ReflectionTestUtils.setField(apiArguments, "contract", "local");

        Assertions.assertThat(apiArguments.isRemoteContract()).isFalse();
    }

    @Test
    void shouldReturnRemoteContractWhenHttp() {
        ApiArguments apiArguments = new ApiArguments();
        ReflectionTestUtils.setField(apiArguments, "contract", "http://localhost/apu.yml");

        Assertions.assertThat(apiArguments.isRemoteContract()).isTrue();
    }


}
