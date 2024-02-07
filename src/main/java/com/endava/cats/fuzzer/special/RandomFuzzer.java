package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.fuzzer.special.mutators.Mutator;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.google.common.collect.Iterators;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Iterator;
import java.util.Set;

/**
 * Fuzzer intended for continuous fuzzing. It will randomly choose fields to fuzz and mutators to apply.
 * The Fuzzer will stop after one of the supplied stopXXX conditions is met: time elapsed, errors occurred or tests executed.
 */
@Singleton
@SpecialFuzzer
public class RandomFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RandomFuzzer.class);
    private final Iterator<String> cycle = Iterators.cycle("\\", "|", "/", "-");
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;
    private final ExecutionStatisticsListener executionStatisticsListener;
    private final MatchArguments matchArguments;
    private final StopArguments stopArguments;
    private final ReportingArguments reportingArguments;
    private final Instance<Mutator> mutators;

    @Inject
    public RandomFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                        ExecutionStatisticsListener executionStatisticsListener,
                        MatchArguments matchArguments, Instance<Mutator> mutators,
                        StopArguments stopArguments, ReportingArguments reportingArguments) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
        this.executionStatisticsListener = executionStatisticsListener;
        this.matchArguments = matchArguments;
        this.mutators = mutators;
        this.stopArguments = stopArguments;
        this.reportingArguments = reportingArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.debug("Skipping fuzzer as payload is empty");
            return;
        }

        long startTime = System.currentTimeMillis();

        boolean shouldStop = false;
        Set<String> allCatsFields = data.getAllFieldsByHttpMethod();

        this.startProgress(data);

        while (!shouldStop) {
            String targetField = CatsUtil.selectRandom(allCatsFields);

            Mutator selectedRandomMutator = CatsUtil.selectRandom(mutators);
            String mutatedPayload = selectedRandomMutator.mutate(data.getPayload(), targetField);

            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .fuzzer(this)
                            .fuzzingData(data)
                            .logger(logger)
                            .payload(mutatedPayload)
                            .scenario("Send a random payload mutating field [%s] with [%s] mutator".formatted(targetField, selectedRandomMutator.name()))
                            .expectedSpecificResponseCode("a response that doesn't shouldStop given arguments")
                            .responseProcessor(this::processResponse)
                            .build());

            updateProgress(data);
            shouldStop = stopArguments.shouldStop(executionStatisticsListener.getErrors(), testCaseListener.getCurrentTestCaseNumber(), startTime);
        }
    }

    private void updateProgress(FuzzingData data) {
        if (!reportingArguments.isSummaryInConsole()) {
            return;
        }
        if (testCaseListener.getCurrentTestCaseNumber() % 20 == 0) {
            ConsoleUtils.renderSameRow(data.getPath() + "  " + data.getMethod(), cycle.next());
        }
    }

    private void startProgress(FuzzingData data) {
        if (!reportingArguments.isSummaryInConsole()) {
            return;
        }
        testCaseListener.notifySummaryObservers(data.getPath(), data.getMethod().name(), 0d);
        ConsoleUtils.renderSameRow(data.getPath() + "  " + data.getMethod(), cycle.next());
    }

     void processResponse(CatsResponse catsResponse, FuzzingData fuzzingData) {
        if (matchArguments.isMatchResponse(catsResponse)) {
            testCaseListener.reportResultError(logger, fuzzingData, "Response matches arguments", "Response matches" + matchArguments.getMatchString());
        } else {
            testCaseListener.skipTest(logger, "Skipping test as response does not match given matchers!");
        }
    }

    @Override
    public String description() {
        return "continuously fuzz random fields with random values based on registered mutators";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
