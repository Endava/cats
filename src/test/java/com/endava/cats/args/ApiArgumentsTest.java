package com.endava.cats.args;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApiArgumentsTest {


    @Test
    void shouldReturnContractEmpty() {
        ApiArguments apiArguments = new ApiArguments();

        ReflectionTestUtils.setField(apiArguments, "contract", "empty");

        Assertions.assertThat(apiArguments.isContractEmpty()).isTrue();
    }

    @Test
    void shouldNotReturnContractEmpty() {
        ApiArguments apiArguments = new ApiArguments();

        ReflectionTestUtils.setField(apiArguments, "contract", "contract");

        Assertions.assertThat(apiArguments.isContractEmpty()).isFalse();
    }

    @Test
    void shouldNotReturnServerEmpty() {
        ApiArguments apiArguments = new ApiArguments();

        ReflectionTestUtils.setField(apiArguments, "server", "contract");

        Assertions.assertThat(apiArguments.isServerEmpty()).isFalse();
    }


    @Test
    void shouldReturnServerEmpty() {
        ApiArguments apiArguments = new ApiArguments();

        ReflectionTestUtils.setField(apiArguments, "server", "empty");

        Assertions.assertThat(apiArguments.isServerEmpty()).isTrue();
    }

    @Test
    void shouldReturnRemoteContract() {
        ApiArguments apiArguments = new ApiArguments();

        ReflectionTestUtils.setField(apiArguments, "contract", "http://localhost");

        Assertions.assertThat(apiArguments.isRemoteContract()).isTrue();
    }

    @Test
    void shouldNotReturnRemoteContract() {
        ApiArguments apiArguments = new ApiArguments();

        ReflectionTestUtils.setField(apiArguments, "contract", "contract");

        Assertions.assertThat(apiArguments.isRemoteContract()).isFalse();
    }

}
