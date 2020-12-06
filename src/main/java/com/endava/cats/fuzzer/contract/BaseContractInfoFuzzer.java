package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class BaseContractInfoFuzzer implements Fuzzer {
    protected static final String DESCRIPTION = "description";
    protected static final String COMMA = ", ";
    protected static final String IS_EMPTY = " is empty";
    protected static final String IS_TOO_SHORT = " is too short";
    protected static final String EMPTY = "";
    protected final TestCaseListener testCaseListener;
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
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

    protected String trailNewLines(String text, int newLines) {
        return text + newLine(newLines);
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(this.runKey(data))) {
            testCaseListener.createAndExecuteTest(log, this, () -> addDefaultsAndProcess(data));

            fuzzedPaths.add(this.runKey(data));
        }
    }

    private void addDefaultsAndProcess(FuzzingData data) {
        testCaseListener.addPath(data.getPath());
        testCaseListener.addFullRequestPath("NA");
        testCaseListener.addRequest(CatsRequest.empty());
        testCaseListener.addResponse(CatsResponse.empty());

        this.process(data);
    }

    protected abstract String runKey(FuzzingData data);

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
