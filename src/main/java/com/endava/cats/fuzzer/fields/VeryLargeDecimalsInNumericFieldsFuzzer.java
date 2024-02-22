package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

/**
 * Fuzzer that sends very large decimals in numeric fields. Size of the large
 * decimals is controlled by the {@code --largeStringsSize} argument.
 */
@Singleton
@FieldFuzzer
public class VeryLargeDecimalsInNumericFieldsFuzzer extends VeryLargeIntegersInNumericFieldsFuzzer {

    /**
     * Creates a new VeryLargeDecimalsInNumericFieldsFuzzer instance.
     *
     * @param sc the service caller
     * @param lr the test case listener
     * @param cp files arguments
     * @param pa to get the size of the large decimals
     */
    public VeryLargeDecimalsInNumericFieldsFuzzer(ServiceCaller sc, TestCaseListener lr, FilesArguments cp, ProcessingArguments pa) {
        super(sc, lr, cp, pa);
    }

    @Override
    public String getTheActualData() {
        return NumberGenerator.generateVeryLargeDecimal(processingArguments.getLargeStringsSize() / 4);
    }
}
