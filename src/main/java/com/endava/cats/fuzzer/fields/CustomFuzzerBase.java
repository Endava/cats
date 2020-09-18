package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.Fuzzer;

import java.util.List;

public interface CustomFuzzerBase extends Fuzzer {

    List<String> reservedWords();
}
