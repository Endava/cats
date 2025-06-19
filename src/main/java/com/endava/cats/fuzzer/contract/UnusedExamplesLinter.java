package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.Linter;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.contract.base.AbstractUnusedElementsLinter;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.examples.Example;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Linter
@Singleton
public class UnusedExamplesLinter extends AbstractUnusedElementsLinter<Example> {
    public UnusedExamplesLinter(TestCaseListener tcl, CatsGlobalContext catsGlobalContext) {
        super(tcl, catsGlobalContext);
    }

    @Override
    protected String getElementType() {
        return "examples";
    }

    @Override
    protected Function<Components, Map<String, Example>> getElementsFunction() {
        return Components::getExamples;
    }
}