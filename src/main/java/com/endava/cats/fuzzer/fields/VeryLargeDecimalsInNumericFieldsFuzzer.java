package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;

import jakarta.inject.Singleton;

@Singleton
@FieldFuzzer
public class VeryLargeDecimalsInNumericFieldsFuzzer extends VeryLargeIntegersInNumericFieldsFuzzer {
    public VeryLargeDecimalsInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cu, cp, pa);
    }

    @Override
    public String getTheActualData() {
        return NumberGenerator.generateVeryLargeDecimal(processingArguments.getLargeStringsSize() / 4);
    }
}
