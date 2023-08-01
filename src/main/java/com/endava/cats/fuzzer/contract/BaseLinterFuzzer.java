package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Base class for all Contract Fuzzers. If you need additional behaviour please make sure you don't break existing Fuzzers.
 * Contract Fuzzers are only focused on contract following best practices without calling the actual service.
 */
public abstract class BaseLinterFuzzer implements Fuzzer {
    protected static final String DESCRIPTION = "description";
    protected static final String COMMA = ", ";
    protected static final String IS_EMPTY = " is empty";
    protected static final String IS_TOO_SHORT = " is too short";
    protected static final String EMPTY = "";
    protected final TestCaseListener testCaseListener;
    protected final List<String> fuzzedPaths = new ArrayList<>();
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    protected BaseLinterFuzzer(TestCaseListener tcl) {
        this.testCaseListener = tcl;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!fuzzedPaths.contains(this.runKey(data))) {
            testCaseListener.createAndExecuteTest(log, this, () -> addDefaultsAndProcess(data));

            fuzzedPaths.add(this.runKey(data));
        }
    }

    /**
     * Contract Fuzzers are only analyzing the contract without doing any HTTP Call.
     * This is why we set the below default values for all Contract Fuzzers.
     *
     * @param data the current FuzzingData
     */
    private void addDefaultsAndProcess(FuzzingData data) {
        testCaseListener.addPath(data.getPath());
        testCaseListener.addContractPath(data.getContractPath());
        testCaseListener.addFullRequestPath("NA");
        CatsRequest request = CatsRequest.empty();
        request.setHttpMethod(String.valueOf(data.getMethod()));
        testCaseListener.addRequest(request);

        this.process(data);
    }

    protected <T> String getOrEmpty(Supplier<T> function, String toReturn) {
        if (function.get() == null) {
            return toReturn;
        }
        return EMPTY;
    }

    /**
     * Each Fuzzer will implement this in order to provide specific logic.
     *
     * @param data the current FuzzingData object
     */
    public abstract void process(FuzzingData data);

    /**
     * This will avoid running the same fuzzer more than once if not relevant. You can define the runKey based on any combination of elements
     * that will make the run unique for the context. Some Fuzzers might run once per contract while others once per path.
     *
     * @param data the FuzzingData
     * @return a unique running key for the Fuzzer context
     */
    protected abstract String runKey(FuzzingData data);

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
