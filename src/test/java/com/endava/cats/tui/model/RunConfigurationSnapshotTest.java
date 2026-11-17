package com.endava.cats.tui.model;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class RunConfigurationSnapshotTest {
    @Test
    void shouldCarryTheExplicitBlackboxModeAndKeepLegacySnapshotsDisabled() {
        CatsConfiguration configuration = new CatsConfiguration("1", "synthetic.yml", "http://localhost", List.of(HttpMethod.GET), 1, 1, 1, 1);

        Assertions.assertThat(RunConfigurationSnapshot.from(configuration, true).blackbox()).isTrue();
        Assertions.assertThat(RunConfigurationSnapshot.from(configuration).blackbox()).isFalse();
        Assertions.assertThat(new RunConfigurationSnapshot("1", "synthetic.yml", "http://localhost", List.of("GET"), 1, 1, 1, 1).blackbox()).isFalse();
    }
}
