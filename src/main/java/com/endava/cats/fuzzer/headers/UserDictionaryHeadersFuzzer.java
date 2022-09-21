package com.endava.cats.fuzzer.headers;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.ConsoleUtils;

import javax.inject.Singleton;

@Singleton
@HeaderFuzzer
public class UserDictionaryHeadersFuzzer implements Fuzzer {
    @Override
    public void fuzz(FuzzingData data) {

    }

    @Override
    public String description() {
        return "iterates through each request headers and sends values from the user supplied dictionary";
    }

    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
