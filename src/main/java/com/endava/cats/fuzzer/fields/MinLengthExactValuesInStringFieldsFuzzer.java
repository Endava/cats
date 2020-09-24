package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class MinLengthExactValuesInStringFieldsFuzzer extends ExactValuesInStringFieldsFuzzer {


    public MinLengthExactValuesInStringFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        super(sc, lr, cu);
    }

    @Override
    protected String exactValueTypeString() {
        return "minLength";
    }

    @Override
    protected Function<Schema, Integer> getExactMethod() {
        return Schema::getMinLength;
    }
}