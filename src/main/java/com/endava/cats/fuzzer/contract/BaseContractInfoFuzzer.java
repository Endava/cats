package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public abstract class BaseContractInfoFuzzer implements Fuzzer {
    protected static final String DESCRIPTION = "description";
    protected static final String COMMA = ", ";
    protected static final String IS_EMPTY = " is empty";
    protected static final String IS_TOO_SHORT = " is too short";
    protected static final String EMPTY = "";
    protected final TestCaseListener testCaseListener;
    protected List<String> fuzzedPaths = new ArrayList<>();

    @Autowired
    protected BaseContractInfoFuzzer(TestCaseListener tcl) {
        this.testCaseListener = tcl;
    }

    public abstract void process(FuzzingData data);


    protected <T> String getOrEmpty(Supplier<T> function, String toReturn) {
        if (function.get() == null) {
            return toReturn;
        }
        return EMPTY;
    }

    protected String bold(String text) {
        return "<strong>" + text + "</strong>";
    }

    protected String newLine(int times) {
        return StringUtils.repeat("<br />", times);
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(this.runKey(data))) {
            testCaseListener.createAndExecuteTest(log, this, () -> process(data));
            fuzzedPaths.add(this.runKey(data));
        }
    }

    protected abstract String runKey(FuzzingData data);

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
