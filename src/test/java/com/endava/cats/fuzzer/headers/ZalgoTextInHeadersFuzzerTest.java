package com.endava.cats.fuzzer.headers;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class ZalgoTextInHeadersFuzzerTest {

    private ZalgoTextInHeadersFuzzer zalgoTextInHeadersFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        zalgoTextInHeadersFuzzer = new ZalgoTextInHeadersFuzzer(serviceCaller, testCaseListener);
    }

    @Test
    void shouldHaveAllMethodsOverridden() {
        Assertions.assertThat(zalgoTextInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(zalgoTextInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(zalgoTextInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(zalgoTextInHeadersFuzzer.matchResponseSchema()).isFalse();
    }

    @Test
    void shouldGetZalgoTextAsPayload() {
        Assertions.assertThat(zalgoTextInHeadersFuzzer.fuzzStrategy()).hasSize(1);
        Assertions.assertThat(zalgoTextInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("c̷̨̛̥̬͉̘̬̻̩͕͚̦̺̻͓̳͇̲̭̝̙̟̈́̉̐͂͒̆͂̿͌̑͐̌̇̈́̾̉̆̀̅̓͛͋̈̄͊̈̄̎̃̒͂̓̊̌̎̌̃́̅͊̏͘͘͘̕̕͘͠͝a̶͖̐͆͑́͆̓͗͆̏̑̈́̾͛̎̂̒̄̏̍͌͛̀́̄̓̍̐͂̀́̈́̂͐̕̕̕̚͘͠͝͠t̵̨̢̨͙̪̼͚͖̲̻̞̦̤̲̖͚̟̯͔̬̜̬͖̺͎̼̬̞̱̳͚͔͎̩̩̩̲̗̩̊̽̈́̔̀̍͒̓̂͐̾̆̐̒̄͂͒̽̾̔͊̒̀͗̿̈́͆͆̂͆̈́̋̏͊̉͌̒̏̓̑͛̉͘͜͜͜͝͝͠͠ş̶̨̢̧̛̛̱̜͈͓̗͍͈̰̱͔̥͙̺̤̠̩̮́̋̒͗̌̔̄̓̓͐̇̾̀́̓̆͗̂̐͊̓̓́̀͌̐̒̆̏̐͐̌̀́̈́̑̄͛̔̌͘̚̕͜͠ͅ ̸̡̡̧̡̨̧̧̯͚̥̙͉̲̠͚̼̤̹̹̳͕̙͔̺̥̼̙̙͚̳̰͕̤͕̀͒̈́̆̆̅̀̑̋̾͒̈́̅͌̀͑͋͋̎͂͂̄̑̆͒̃̓́̂̈́̑̄͝į̴̬͙͕̤͎͇̹̮̯̞̦̱̠̤̖̣̆͊̀̀̓͛͗͛̈͂̌̉̊͐̆̈̉͂͌̊́̉̋͘̚̚͜͝͝ș̷̡̛̛̮̲̥͙̞̤̘̉͛͗̿͂̏͛̾̂̂̄͗́̈́́̅̄̇̈́͗̀̂̈̉̐͑̏̒̈́͗̆͆̆̆͐̀͋̋͌̚̚͝͝ ̴̧̢̛̥̼̘̬̮͚͙̙̳͇̣̬̓̽̃̇̅͆͌̓̒̾͌̒͋͆́̓͛̔͛͒̉̔̏̔̂͐͛͗̾̎͂̏̋͘̚͝͝ç̵̡̧̛̛̟̩̲̲̲̫̺͎͎̘͎̘̱̭̬̗̎̾̏̂̏͑͊̾̎̂̉̊̉̐̓̾͒̓̓̒̔̽̄́͋̀̈́́̓̏͑͗̂̂̈́̒̚͘̕͘͝͠͝ͅͅͅơ̶̛̩̫̊̿̇͊͆́̅̈̽̆̓͛̌͐̍̀͒̐͑̀̎̀̀̉̑͛̔͋́̀͂̈̐̾̊̓͑̔͐̚̕͝͝͝͝͠ô̷̡̧̧̨̢̱͈̠̬̤̪̖̘͍̥̝͍̺̠̮̫̺̳͚͈͕̞̯̳̩̗̜̺̜̠͔̖̥͆͛͑́̆͛͐̓̒͊̊͑̽̄̐͊̓̃̚͜͜͝l");
    }
}
