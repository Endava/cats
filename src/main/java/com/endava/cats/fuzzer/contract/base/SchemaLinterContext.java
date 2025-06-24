package com.endava.cats.fuzzer.contract.base;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record SchemaLinterContext<E>(String scenarioMessage,
                                     String expectedResult,
                                     Supplier<Map<SchemaLocation, E>> collector,
                                     BiPredicate<SchemaLocation, FuzzingData> filter,
                                     Predicate<E> validator,
                                     BiFunction<SchemaLocation, E, String> format,
                                     String successMessage,
                                     String failureMessage,
                                     Function<FuzzingData, String> runKeyProvider) {
}