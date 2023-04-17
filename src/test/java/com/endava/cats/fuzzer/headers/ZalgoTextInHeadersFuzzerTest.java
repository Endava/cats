package com.endava.cats.fuzzer.headers;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.strategy.FuzzingStrategy;
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
        HeadersIteratorExecutor headersIteratorExecutor = new HeadersIteratorExecutor(serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(IgnoreArguments.class));
        zalgoTextInHeadersFuzzer = new ZalgoTextInHeadersFuzzer(headersIteratorExecutor);
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
        Assertions.assertThat(zalgoTextInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo(" ̵̡̡̢̡̨̨̢͚̬̱̤̰̗͉͚̖͙͎͔͔̺̳͕̫̬͚̹͖̬̭̖̪̗͕̜̣̥̣̼͍͉̖͍̪͈̖͚̙͛͒͂̎̊̿̀̅̈͌͋̃̾̈̾̇͛͌͘͜͜͠͝ͅͅͅ ̷͕̗̇͛̅̀̑̇̈͗͌͛̐̀͆̐̊̅̋̈́̂̈́̈́͑̓͂͂̌̈́̽͌͐̐͂͐̈́̍̂͗̂͘͠͝͝͝ͅ ̷̨̢̧̢̡̨̛͕̯̭̹͖̮̘̤̩̥̟̖͈̯̠̖͈̜͈̥̫͔̘̭͉͎͇̤̦̯͙̹̠̼̮͕̲̖̟̲̦̣͇̳͖̳̭͇͓̭͌̓̀̅̋̋̀̈́̎̄͛̾̊͐̎̉̏͊͐̑͊͒̐̔̏̔̋̑̌͆̏̀̉͆̆́̓̆̉̀̒̆̆̉̀̂̎̈̔͗̔̕̕͘̕̚̚̕͘͜͝͝͝͝͝͠ͅ ̷̧̡̥͈͓͙͈̫͙͎͈̻̔̊̎̏̑̒̐̐̆̉̍͠͝͝ ̴̡̛̛͓͎͇̘͈͇̱̟̠̳͇̬̺̲̭̪̬̼̝̠̙̹̩̱̪͔͉͎̱͚͍̬͈̤͈͙͖̝̲̦̞̺̟̟̺͇̳͈̠̘̺̪̱̮̉̀̍̏̐̃̅̐̊̾͆̐͋͊̿̉̆̾͊̀͊͒͌̀͛̎́́͂̐͂̎͛̆͜͜͜͠ͅ ̶̧̧͖̻̥̝̺̼̙̫̩̹̣̲̩̲͍̺̘͕̤͉̹̥͉̮̮̟̘̥̺̯̗̠͈̬͚̦̦͚̫̫̦̉́̾̀̅͋̋̇̕̕͜͜͝ͅͅ ̶̧̛̛̝̟̤̬̙͔̻͙͚̹̣̳̳͔̥̘̠̗̦̠͚͎̖̮̳̗̥̫͚̯̬̩̎́̽͒̋̓̀͂̈́̓́̎͐͊͒̎͒͌̿̿̔͐̈́͑̊̄̓̎͐̓̓̍͘̕̚̚͜͜ ̶̢̡̡̨̡̡̘̫̫̠̟̻̳̻͈̲̖͚͇̼̩̥̥͎̥̯͚̞̘̼̞͍̮̗͈̱͚͙̠͔̞̮̱̭͍͍̪̲̜͓͍̣̯̲̠̲̤̅͊̑̇̆́̈́̓̿̄̐̓̐͐́͛̆͜͝͝͝͠ͅ ̶̧̡̨̧̡̧̥̥̱̪͇̞̭͙͚͔̜̠͓͈̞͈̣̹̝̩̦̟̻̰͙̯̼̜̞̮̬̝͚̺̟͎̻̱̙̦̜̭̲̰͎̳̣̈͜͜͜ͅ ̸̹̟̯̝͚̪̼͓͕͕̹͖̣̠͓̫͇͚͔̼̊́͑̊̊̅͗͠ͅ".replace(" ", ""));
    }
}
