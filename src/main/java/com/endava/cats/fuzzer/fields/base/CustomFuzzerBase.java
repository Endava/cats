package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.fuzzer.api.Fuzzer;

import java.util.List;

public interface CustomFuzzerBase extends Fuzzer {

    List<String> reservedWords();
}
