package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Base class for all Contract Fuzzers. If you need additional behaviour please make sure you don't break existing Fuzzers.
 * Contract Fuzzers are only focused on contract following best practices without calling the actual service.
 */
public abstract class BaseLinterFuzzer implements Fuzzer {
    /**
     * Represents a comma followed by a space.
     */
    protected static final String COMMA = ", ";
    /**
     * Represents "" string.
     */
    protected static final String EMPTY = "";
    /**
     * Represents the N/A string.
     */
    private static final String N_A = "N/A";

    /**
     * The test case listener.
     */
    protected final TestCaseListener testCaseListener;
    private final List<String> fuzzedPaths = new ArrayList<>();
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    /**
     * Creates a new instance of subclasses.
     *
     * @param tcl the test case listener
     */
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

    /**
     * Checks each element in the given array against a specified predicate and constructs a comma-separated
     * string of elements that satisfy the predicate. The resulting string is stripped of leading commas and spaces.
     *
     * @param pathElements  The array of strings to be checked.
     * @param checkFunction The predicate used to test each element in the array.
     *                      Elements that satisfy the predicate will be included in the result.
     * @return A comma-separated string of elements that satisfy the predicate,
     * or {@code N_A} if no elements meet the criteria.
     */
    protected String check(String[] pathElements, Predicate<String> checkFunction) {
        StringBuilder result = new StringBuilder();

        for (String pathElement : pathElements) {
            if (checkFunction.test(pathElement)) {
                result.append(COMMA).append(pathElement);
            }
        }

        if (!result.toString().isEmpty()) {
            return StringUtils.stripStart(result.toString().trim(), ", ");
        }

        return N_A;
    }

    /**
     * Checks if the given string represents an error condition.
     * The method returns true if the string is not equal to the constant {@code N_A}, indicating the presence of errors.
     *
     * @param s The string to be checked for error conditions.
     * @return {@code true} if the string represents an error condition, {@code false} otherwise.
     */
    protected boolean hasErrors(String s) {
        return !N_A.equals(s);
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
