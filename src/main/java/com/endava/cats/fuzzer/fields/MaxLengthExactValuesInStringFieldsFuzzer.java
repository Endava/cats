package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@FieldFuzzer
public class MaxLengthExactValuesInStringFieldsFuzzer extends ExactValuesInFieldsFuzzer {


    public MaxLengthExactValuesInStringFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, CatsParams cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String exactValueTypeString() {
        return "maxLength";
    }

    @Override
    protected Function<Schema, Number> getExactMethod() {
        return Schema::getMaxLength;
    }
}