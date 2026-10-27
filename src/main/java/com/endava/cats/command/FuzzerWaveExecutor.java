package com.endava.cats.command;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.util.ProfileLoader;
import com.endava.cats.exception.CatsExecutionCancelledException;
import com.endava.cats.exception.CatsExecutionLimitReachedException;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.fields.OnlyRequiredFieldsFuzzer;
import com.endava.cats.fuzzer.http.HappyPathFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFlowFuzzer;
import com.endava.cats.fuzzer.special.FunctionalFuzzer;
import com.endava.cats.fuzzer.special.RandomFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.util.AnnotationUtils;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates path ordering, collection GET pre-seeding, breadth-first fuzzer waves (loaded from
 * {@code fuzzer-profiles.yml}), deferred DELETE execution, producer pool refilling, and optional
 * time-budget filling with {@link RandomFuzzer}.
 */
final class FuzzerWaveExecutor {
    private static final String SEPARATOR = "-".repeat(ConsoleUtils.getConsoleColumns(22));
    private static final int MAX_CONTINUOUS_RANDOM_ROUNDS = 200;
    private static final long MAX_CLEANUP_GRACE_MILLIS = 5_000;
    private static final long CLEANUP_GRACE_BUDGET_DIVISOR = 20;

    private final CatsCommand command;
    private final PrettyLogger logger;
    private final List<DeferredHappyPathDelete> deferredHappyPathDeletes = new ArrayList<>();
    private final Set<String> pathsWithObservedLiveResources = new HashSet<>();
    private final Set<String> producersRefilledInWave = new HashSet<>();
    private Map<String, Set<String>> producersByConsumer = Map.of();
    private Map<String, PreparedPath> preparedPathsByKey = Map.of();
    private LongSupplier clock = System::currentTimeMillis;
    private long cleanupDeadlineMillis = Long.MAX_VALUE;
    private boolean cleanupGraceExpiryLogged;
    private boolean replayingProducer;

    private record DeferredHappyPathDelete(List<FuzzingData> pathData, List<FuzzingData> deleteData,
                                           List<Fuzzer> deleteFuzzers) {
    }

    private final class PreparedPath {
        private final String pathKey;
        private final PathItem pathItem;
        private final OpenAPI openAPI;
        private final boolean binaryStreamOnly;
        private List<FuzzingData> filteredFuzzingData;
        private List<Fuzzer> fuzzersToRun;
        private boolean initialized;

        PreparedPath(String pathKey, PathItem pathItem, OpenAPI openAPI, boolean binaryStreamOnly) {
            this.pathKey = pathKey;
            this.pathItem = pathItem;
            this.openAPI = openAPI;
            this.binaryStreamOnly = binaryStreamOnly;
        }

        String pathKey() {
            return pathKey;
        }

        boolean binaryStreamOnly() {
            return binaryStreamOnly;
        }

        private void ensureInitialized() {
            if (initialized) {
                return;
            }
            initialized = true;
            List<FuzzingData> fuzzingDataList = command.fuzzingDataFactory.fromPathItem(pathKey, pathItem, openAPI);
            if (fuzzingDataList.isEmpty()) {
                logger.warning("There was a problem fuzzing path {}. You might want to enable debug mode for more details. Additionally, you can log a GitHub issue at: https://github.com/Endava/cats/issues.", pathKey);
                this.filteredFuzzingData = List.of();
                this.fuzzersToRun = List.of();
                return;
            }
            this.filteredFuzzingData = fuzzingDataList.stream()
                    .filter(fuzzingData -> command.filterArguments.isHttpMethodSupplied(fuzzingData.getMethod()))
                    .filter(fuzzingData -> command.processingArguments.matchesXxxSelection(fuzzingData.getPayload()))
                    .toList();
            Set<HttpMethod> allHttpMethodsFromFuzzingData = filteredFuzzingData.stream()
                    .map(FuzzingData::getMethod)
                    .collect(Collectors.toSet());
            this.fuzzersToRun = command.filterArguments.filterOutFuzzersNotMatchingHttpMethodsAndPath(
                    allHttpMethodsFromFuzzingData, pathKey);
        }

        List<FuzzingData> filteredFuzzingData() {
            ensureInitialized();
            return filteredFuzzingData;
        }

        List<Fuzzer> fuzzersToRun() {
            ensureInitialized();
            return fuzzersToRun;
        }

        boolean isRunnable() {
            return !binaryStreamOnly && (!filteredFuzzingData().isEmpty() || !fuzzersToRun().isEmpty());
        }
    }

    FuzzerWaveExecutor(CatsCommand command, PrettyLogger logger) {
        this.command = command;
        this.logger = logger;
    }

    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    private ProfileLoader activeProfileLoader() {
        if (command.filterArguments != null && command.filterArguments.getProfileLoader() != null) {
            return command.filterArguments.getProfileLoader();
        }
        return new ProfileLoader();
    }

    static Set<String> loadWaveFuzzers(ProfileLoader profileLoader, String profileName) {
        return profileLoader.getProfile(profileName)
                .map(profile -> profile.fuzzers().stream()
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .map(Collections::unmodifiableSet)
                .orElse(Set.of());
    }

    void startFuzzing(OpenAPI openAPI) {
        List<String> suppliedPaths = command.filterArguments.getPathsToRun(openAPI);
        deferredHappyPathDeletes.clear();
        pathsWithObservedLiveResources.clear();
        boolean reuseResources = command.processingArguments.isReuseSuccessfulResources();
        List<Map.Entry<String, PathItem>> paths = new ArrayList<>(sortPathsAlphabetically(openAPI, command.filesArguments.getPathsOrder()));
        if (reuseResources && command.filesArguments.getPathsOrder().isEmpty() &&
                command.filterArguments.isHttpMethodSupplied(HttpMethod.POST)) {
            paths = OperationDependencyOrder.order(openAPI, paths);
        }
        producersByConsumer = reuseResources ? OperationDependencyOrder.producersByConsumer(openAPI, paths) : Map.of();
        preseedCollectionGets(paths, suppliedPaths, openAPI);

        List<PreparedPath> preparedPaths = preparePathsForExecution(paths, suppliedPaths, openAPI);
        preparedPathsByKey = preparedPaths.stream().collect(Collectors.toMap(PreparedPath::pathKey, Function.identity()));
        if (command.testCaseListener != null) {
            command.testCaseListener.setBeforeDeleteTestCallback(this::refillIfEmptied);
        }
        boolean slicePerOperation = shouldRunContinuousRandom() && hasMultipleOperationsInScope(preparedPaths);
        if (command.randomFuzzer != null) {
            command.randomFuzzer.setMultipleOperationsInScope(slicePerOperation);
        }

        if (!reuseResources || command.filterArguments.isDryRun()) {
            try {
                for (PreparedPath path : preparedPaths) {
                    CatsExecutionCancelledException.check();
                    fuzzPreparedPath(path);
                }
                runDeferredHappyPathDeletes();
                runContinuousRandomUntilTimeLimit(preparedPaths);
            } finally {
                if (command.randomFuzzer != null) {
                    command.randomFuzzer.setMultipleOperationsInScope(false);
                }
            }
            return;
        }

        boolean unparameterizedBinaryRan = false;
        try {
            runPhasedWavesAcrossPaths(preparedPaths);
            runBinaryStreamPaths(preparedPaths, true);
            runDeferredHappyPathDeletes();
            runBinaryStreamPaths(preparedPaths, false);
            unparameterizedBinaryRan = true;
            runContinuousRandomUntilTimeLimit(preparedPaths);
        } catch (CatsExecutionLimitReachedException e) {
            boolean binaryAlreadyRan = unparameterizedBinaryRan;
            runWithinCleanupGrace(() -> runLimitReachedCleanup(preparedPaths, binaryAlreadyRan));
            throw e;
        } finally {
            if (command.randomFuzzer != null) {
                command.randomFuzzer.setMultipleOperationsInScope(false);
            }
        }
    }

    private void runLimitReachedCleanup(List<PreparedPath> preparedPaths, boolean unparameterizedBinaryRan) {
        runDeferredHappyPathDeletes();
        if (!unparameterizedBinaryRan) {
            runBinaryStreamHappyPathOnly(preparedPaths);
        }
    }

    private boolean hasMultipleOperationsInScope(List<PreparedPath> preparedPaths) {
        if (preparedPaths.size() > 1) {
            return true;
        }
        return preparedPaths.size() == 1 && preparedPaths.getFirst().filteredFuzzingData().size() > 1;
    }

    private List<PreparedPath> preparePathsForExecution(List<Map.Entry<String, PathItem>> paths,
                                                        List<String> suppliedPaths, OpenAPI openAPI) {
        List<PreparedPath> preparedPaths = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : paths) {
            CatsExecutionCancelledException.check();
            if (!suppliedPaths.contains(entry.getKey())) {
                logger.skip("Skipping path {}", entry.getKey());
                continue;
            }
            boolean binaryStreamOnly = entry.getValue().getGet() != null &&
                    entry.getValue().readOperationsMap().size() == 1 &&
                    isBinaryStreamOnlyResponse(entry.getValue().getGet());
            preparedPaths.add(new PreparedPath(entry.getKey(), entry.getValue(), openAPI, binaryStreamOnly));
        }
        return preparedPaths;
    }

    private void runPhasedWavesAcrossPaths(List<PreparedPath> preparedPaths) {
        ProfileLoader profileLoader = activeProfileLoader();
        Set<String> coverageWaveFuzzers = loadWaveFuzzers(profileLoader, "coverage");
        Set<String> crashWaveFuzzers = loadWaveFuzzers(profileLoader, "crash");
        runSeedWave(preparedPaths);
        runCoverageWave(preparedPaths, coverageWaveFuzzers);
        runCrashWave(preparedPaths, coverageWaveFuzzers, crashWaveFuzzers);
        runRemainingWaveAndSecondPhase(preparedPaths, coverageWaveFuzzers, crashWaveFuzzers);
    }

    private void runSeedWave(List<PreparedPath> preparedPaths) {
        command.executionEventPublisher.publish(new CatsExecutionEvent.WaveStarted(Instant.now(), 1, 4, "Seed"));
        for (PreparedPath path : preparedPaths) {
            if (path.isRunnable()) {
                CatsExecutionCancelledException.check();
                logger.start(AnsiUtils.bold("Start fuzzing path {}"), path.pathKey());
                command.executionEventPublisher.publish(new CatsExecutionEvent.PathStarted(Instant.now(), path.pathKey()));
                runSeedFuzzersForPath(path.filteredFuzzingData(), path.fuzzersToRun());
            }
        }
    }

    private void runCoverageWave(List<PreparedPath> preparedPaths, Set<String> coverageWaveFuzzers) {
        long resourcesBeforeCoverageWave = command.runtimeResourcePool != null ? command.runtimeResourcePool.observedResourceCount() : 0;
        command.executionEventPublisher.publish(new CatsExecutionEvent.WaveStarted(Instant.now(), 2, 4, "Coverage"));
        producersRefilledInWave.clear();
        for (String coverageFuzzerName : coverageWaveFuzzers) {
            Set<String> singleWave = Set.of(coverageFuzzerName);
            runWave(preparedPaths, fuzzer -> isInWave(fuzzer, singleWave), _ -> {
            });
        }
        if (command.runtimeResourcePool != null && command.runtimeResourcePool.observedResourceCount() > resourcesBeforeCoverageWave) {
            retryUnresolvedSeedOperations(preparedPaths);
        }
    }

    private void runCrashWave(List<PreparedPath> preparedPaths, Set<String> coverageWaveFuzzers, Set<String> crashWaveFuzzers) {
        command.executionEventPublisher.publish(new CatsExecutionEvent.WaveStarted(Instant.now(), 3, 4, "Crash"));
        producersRefilledInWave.clear();
        for (String crashFuzzerName : crashWaveFuzzers) {
            if (!coverageWaveFuzzers.contains(crashFuzzerName)) {
                Set<String> singleWave = Set.of(crashFuzzerName);
                runWave(preparedPaths, fuzzer -> isInWave(fuzzer, singleWave), _ -> {
                });
            }
        }
    }

    private void runRemainingWaveAndSecondPhase(List<PreparedPath> preparedPaths,
                                                Set<String> coverageWaveFuzzers, Set<String> crashWaveFuzzers) {
        command.executionEventPublisher.publish(new CatsExecutionEvent.WaveStarted(Instant.now(), 4, 4, "Full"));
        producersRefilledInWave.clear();
        Predicate<Fuzzer> remainingFilter =
                fuzzer -> !isInWave(fuzzer, coverageWaveFuzzers) && !isInWave(fuzzer, crashWaveFuzzers);
        runWave(preparedPaths, remainingFilter,
                path -> runSecondPhaseFuzzers(path.filteredFuzzingData(), SecondPhaseFuzzer.Trigger.PATH_COMPLETED));
        for (PreparedPath path : preparedPaths.reversed()) {
            if (path.isRunnable()) {
                command.executionEventPublisher.publish(new CatsExecutionEvent.PathCompleted(Instant.now(), path.pathKey()));
            }
        }
    }

    /**
     * Runs one wave in two passes: non-DELETE operations in dependency order, then DELETE operations in reverse
     * path order (children first) with on-demand producer pool refill.
     */
    private void runWave(List<PreparedPath> preparedPaths, Predicate<Fuzzer> waveFilter,
                         Consumer<PreparedPath> afterNonDeletePhase) {
        boolean waveHasFuzzers = preparedPaths.stream().filter(PreparedPath::isRunnable)
                .anyMatch(path -> !waveFuzzers(path, waveFilter).isEmpty());
        if (waveHasFuzzers) {
            preparedPaths.stream().filter(PreparedPath::isRunnable).map(PreparedPath::pathKey).forEach(this::refillIfEmptied);
        }
        for (PreparedPath path : preparedPaths) {
            if (path.isRunnable()) {
                CatsExecutionCancelledException.check();
                runFuzzers(nonDeleteData(path.filteredFuzzingData()), waveFuzzers(path, waveFilter));
                afterNonDeletePhase.accept(path);
            }
        }
        for (PreparedPath path : preparedPaths.reversed()) {
            if (path.isRunnable()) {
                CatsExecutionCancelledException.check();
                runDeleteOperations(deleteData(path.filteredFuzzingData()), waveFuzzers(path, waveFilter));
            }
        }
    }

    private List<Fuzzer> waveFuzzers(PreparedPath path, Predicate<Fuzzer> waveFilter) {
        return nonSeedFuzzers(path.fuzzersToRun()).stream().filter(waveFilter).toList();
    }

    private void runDeleteOperations(List<FuzzingData> deleteData, List<Fuzzer> fuzzers) {
        if (deleteData.isEmpty()) {
            return;
        }
        for (Fuzzer fuzzer : orderedBySuccessLikelihood(fuzzers)) {
            runFuzzers(deleteData, List.of(fuzzer));
            deleteData.stream().map(FuzzingData::getPath).distinct().forEach(this::refillIfEmptied);
        }
    }

    static List<Fuzzer> orderedBySuccessLikelihood(List<Fuzzer> fuzzers) {
        return fuzzers.stream().sorted(Comparator.comparingInt(FuzzerWaveExecutor::successLikelihood)).toList();
    }

    private static int successLikelihood(Fuzzer fuzzer) {
        if (fuzzer instanceof HappyPathFuzzer) {
            return 4;
        }
        if (fuzzer instanceof OnlyRequiredFieldsFuzzer) {
            return 3;
        }
        if (AnnotationUtils.findAnnotation(fuzzer.getClass(), HeaderFuzzer.class) != null) {
            return 2;
        }
        return AnnotationUtils.findAnnotation(fuzzer.getClass(), HttpFuzzer.class) != null ? 1 : 0;
    }

    /**
     * Replays the producers of a consumer path once per wave when its resources were observed earlier but are now
     * all gone, so later operations do not run against stale identifiers.
     */
    private void refillIfEmptied(String consumerPath) {
        if (replayingProducer) {
            return;
        }
        Set<String> producers = producersByConsumer.getOrDefault(consumerPath, Set.of());
        if (command.runtimeResourcePool == null || producers.isEmpty()) {
            return;
        }
        if (command.runtimeResourcePool.hasLiveResourceFor(consumerPath)) {
            pathsWithObservedLiveResources.add(consumerPath);
            return;
        }
        if (!pathsWithObservedLiveResources.contains(consumerPath)) {
            return;
        }
        replayingProducer = true;
        try {
            List<PreparedPath> toReplay = producers.stream()
                    .filter(producerKey -> !hasLiveIntermediateParent(consumerPath, producerKey))
                    .filter(producersRefilledInWave::add)
                    .map(preparedPathsByKey::get)
                    .filter(Objects::nonNull)
                    .toList();
            toReplay.forEach(producer -> replayProducer(producer, consumerPath));
            if (command.runtimeResourcePool.hasLiveResourceFor(consumerPath)) {
                toReplay.forEach(producer -> producersRefilledInWave.remove(producer.pathKey()));
            }
        } finally {
            replayingProducer = false;
        }
    }

    private boolean hasLiveIntermediateParent(String consumerPath, String producerPath) {
        if (!consumerPath.startsWith(producerPath + "/")) {
            return false;
        }
        int nextSlash = consumerPath.indexOf('/', producerPath.length() + 1);
        if (nextSlash < 0) {
            return false;
        }
        String intermediateItemPath = consumerPath.substring(0, nextSlash);
        return intermediateItemPath.endsWith("}") &&
                command.runtimeResourcePool.hasLiveResourceFor(intermediateItemPath);
    }

    private void replayProducer(PreparedPath producer, String consumerPath) {
        Optional<Fuzzer> happyPath = producer.fuzzersToRun().stream().filter(HappyPathFuzzer.class::isInstance).findFirst();
        List<FuzzingData> safeSeeds = producer.filteredFuzzingData().stream().filter(this::isSafeResourceSeed).toList();
        List<FuzzingData> posts = safeSeeds.stream().filter(data -> data.getMethod() == HttpMethod.POST).toList();
        List<FuzzingData> replayData = posts.isEmpty()
                ? safeSeeds.stream().filter(data -> data.getMethod() == HttpMethod.GET).toList() : posts;
        if (happyPath.isEmpty() || replayData.isEmpty()) {
            return;
        }
        logger.note("No live resources left for {}; replaying producer {}", consumerPath, producer.pathKey());
        runFuzzers(replayData, List.of(happyPath.get()));
    }

    private void retryUnresolvedSeedOperations(List<PreparedPath> preparedPaths) {
        if (command.runtimeResourcePool == null) {
            return;
        }
        for (PreparedPath path : preparedPaths) {
            if (!path.binaryStreamOnly()) {
                retryUnresolvedSeedForPath(path);
            }
        }
    }

    private void retryUnresolvedSeedForPath(PreparedPath path) {
        List<Fuzzer> seedFuzzers = seedFuzzers(path.fuzzersToRun());
        if (seedFuzzers.isEmpty()) {
            return;
        }
        List<FuzzingData> unresolvedSeedData = path.filteredFuzzingData().stream()
                .filter(this::isSafeResourceSeed)
                .filter(data -> !command.runtimeResourcePool.hasSucceededWithUnmodifiedRequest(data.getPath(), data.getMethod()))
                .sorted(Comparator.comparingInt(data -> seedMethodOrder(data.getMethod())))
                .toList();
        if (!unresolvedSeedData.isEmpty()) {
            runFuzzers(unresolvedSeedData, seedFuzzers);
        }
    }

    private void runBinaryStreamPaths(List<PreparedPath> preparedPaths, boolean parameterized) {
        for (PreparedPath path : preparedPaths) {
            if (!path.binaryStreamOnly() || path.pathKey().contains("{") != parameterized) {
                continue;
            }
            CatsExecutionCancelledException.check();
            logger.start(AnsiUtils.bold("Start fuzzing path {}"), path.pathKey());
            command.executionEventPublisher.publish(new CatsExecutionEvent.PathStarted(Instant.now(), path.pathKey()));
            runFirstPhaseFuzzers(path.filteredFuzzingData(), path.fuzzersToRun());
            runSecondPhaseFuzzers(path.filteredFuzzingData(), SecondPhaseFuzzer.Trigger.PATH_COMPLETED);
            command.executionEventPublisher.publish(new CatsExecutionEvent.PathCompleted(Instant.now(), path.pathKey()));
        }
    }

    private void runBinaryStreamHappyPathOnly(List<PreparedPath> preparedPaths) {
        for (PreparedPath path : preparedPaths) {
            if (!path.binaryStreamOnly() || path.pathKey().contains("{")) {
                continue;
            }
            CatsExecutionCancelledException.check();
            List<Fuzzer> happyOnly = path.fuzzersToRun().stream().filter(HappyPathFuzzer.class::isInstance).toList();
            if (!happyOnly.isEmpty()) {
                runFuzzers(path.filteredFuzzingData(), happyOnly);
            }
        }
    }

    private void runContinuousRandomUntilTimeLimit(List<PreparedPath> preparedPaths) {
        if (!shouldRunContinuousRandom()) {
            return;
        }
        List<FuzzingData> nonEmptyData = preparedPaths.stream()
                .flatMap(path -> path.filteredFuzzingData().stream())
                .filter(data -> !JsonUtils.isEmptyPayload(data.getPayload()))
                .toList();
        if (nonEmptyData.isEmpty()) {
            return;
        }
        for (int round = 0; round < MAX_CONTINUOUS_RANDOM_ROUNDS; round++) {
            for (FuzzingData data : nonEmptyData) {
                CatsExecutionCancelledException.check();
                runSingleFuzzer(command.randomFuzzer, data, command.randomFuzzer::fuzzContinuous);
            }
        }
    }

    private boolean shouldRunContinuousRandom() {
        if (command.stopArguments == null || command.stopArguments.getStopAfterTimeInSec() <= 0 ||
                command.filterArguments.isDryRun() || command.reportingArguments.isTui() || command.randomFuzzer == null) {
            return false;
        }
        boolean skipped = Optional.ofNullable(command.filterArguments.getSkipFuzzers()).orElse(List.of()).stream()
                .anyMatch(name -> ConsoleUtils.matchesFuzzer(name, RandomFuzzer.class));
        if (skipped) {
            return false;
        }
        if (command.stopArguments.isFillTimeBudget()) {
            return true;
        }
        return Optional.ofNullable(command.filterArguments.getSuppliedFuzzers()).orElse(List.of()).stream()
                .anyMatch(name -> ConsoleUtils.matchesFuzzer(name, RandomFuzzer.class));
    }

    private static boolean isSeedFuzzer(Fuzzer fuzzer) {
        return fuzzer instanceof HappyPathFuzzer || fuzzer instanceof OnlyRequiredFieldsFuzzer;
    }

    private static List<Fuzzer> nonSeedFuzzers(List<Fuzzer> configuredFuzzers) {
        if (configuredFuzzers.stream().noneMatch(HappyPathFuzzer.class::isInstance)) {
            return configuredFuzzers;
        }
        return configuredFuzzers.stream().filter(fuzzer -> !isSeedFuzzer(fuzzer)).toList();
    }

    private static List<Fuzzer> seedFuzzers(List<Fuzzer> configuredFuzzers) {
        Optional<Fuzzer> happyPathFuzzer = configuredFuzzers.stream().filter(HappyPathFuzzer.class::isInstance).findFirst();
        if (happyPathFuzzer.isEmpty()) {
            return List.of();
        }
        return Stream.concat(happyPathFuzzer.stream(),
                configuredFuzzers.stream().filter(OnlyRequiredFieldsFuzzer.class::isInstance).limit(1)).toList();
    }

    private boolean isInWave(Fuzzer fuzzer, Set<String> waveLowerNames) {
        String sanitized = ConsoleUtils.removeTrimSanitize(fuzzer.toString()).toLowerCase(Locale.ROOT);
        String simple = fuzzer.getClass().getSimpleName().replace("Fuzzer", "").toLowerCase(Locale.ROOT);
        return waveLowerNames.contains(sanitized) || waveLowerNames.contains(simple);
    }

    private void preseedCollectionGets(List<Map.Entry<String, PathItem>> paths, List<String> suppliedPaths, OpenAPI openAPI) {
        if (!command.processingArguments.isPreseedCollectionGets() || !command.processingArguments.isReuseSuccessfulResources() ||
                command.filterArguments.isDryRun() || command.reportingArguments.isTui() ||
                !command.filterArguments.isHttpMethodSupplied(HttpMethod.GET)) {
            return;
        }
        int seeded = 0;
        for (Map.Entry<String, PathItem> entry : paths) {
            if (seeded >= command.processingArguments.getMaxPreseedCollectionGets()) {
                return;
            }
            if (preseedPathIfEligible(entry.getKey(), entry.getValue(), suppliedPaths, openAPI)) {
                seeded++;
            }
        }
    }

    private boolean preseedPathIfEligible(String path, PathItem item, List<String> suppliedPaths, OpenAPI openAPI) {
        if (!isEligibleForPreseed(path, item, suppliedPaths)) {
            return false;
        }
        List<Fuzzer> configured = command.filterArguments.filterOutFuzzersNotMatchingHttpMethodsAndPath(Set.of(HttpMethod.GET), path);
        Fuzzer happyPath = configured.stream().filter(HappyPathFuzzer.class::isInstance).findFirst().orElse(null);
        if (happyPath == null) {
            return false;
        }
        List<FuzzingData> getData = command.fuzzingDataFactory.fromPathItem(path, item, openAPI).stream()
                .filter(data -> data.getMethod() == HttpMethod.GET)
                .filter(data -> command.processingArguments.matchesXxxSelection(data.getPayload()))
                .limit(1).toList();
        if (getData.isEmpty()) {
            return false;
        }
        runFuzzers(getData, List.of(happyPath));
        return true;
    }

    private boolean isEligibleForPreseed(String path, PathItem item, List<String> suppliedPaths) {
        return suppliedPaths.contains(path) && !path.contains("{") && item.getGet() != null &&
                !hasRequiredQueryParameters(item) && !isBinaryStreamOnlyResponse(item.getGet());
    }

    private boolean isBinaryStreamOnlyResponse(Operation operation) {
        if (operation.getResponses() == null) {
            return false;
        }
        List<String> twoXxMediaTypes = operation.getResponses().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("2") &&
                        entry.getValue() != null && entry.getValue().getContent() != null)
                .flatMap(entry -> entry.getValue().getContent().keySet().stream())
                .toList();
        return !twoXxMediaTypes.isEmpty() && twoXxMediaTypes.stream()
                .allMatch(mt -> mt.contains("octet-stream") || mt.startsWith("image/") ||
                        mt.startsWith("video/") || mt.startsWith("audio/"));
    }

    private boolean hasRequiredQueryParameters(PathItem item) {
        return Stream.concat(
                        Optional.ofNullable(item.getParameters()).orElse(List.of()).stream(),
                        Optional.ofNullable(item.getGet().getParameters()).orElse(List.of()).stream())
                .anyMatch(parameter -> "query".equals(parameter.getIn()) && Boolean.TRUE.equals(parameter.getRequired()));
    }

    void runDeferredHappyPathDeletes() {
        List<DeferredHappyPathDelete> deferred = List.copyOf(deferredHappyPathDeletes).reversed();
        deferredHappyPathDeletes.clear();
        for (DeferredHappyPathDelete delete : deferred) {
            if (!delete.deleteData().isEmpty()) {
                CatsExecutionCancelledException.check();
                runFuzzers(delete.deleteData(), delete.deleteFuzzers());
                runSecondPhaseFuzzers(delete.pathData(), SecondPhaseFuzzer.Trigger.PATH_RESOURCES_DELETED);
            }
        }
    }

    /**
     * Runs cleanup after an execution limit was reached, bounded by {@code min(5s, 5% of --stopAfterTimeInSec)}.
     */
    private void runWithinCleanupGrace(Runnable cleanup) {
        long graceMillis = cleanupGraceMillis();
        cleanupDeadlineMillis = clock.getAsLong() + graceMillis;
        cleanupGraceExpiryLogged = false;
        try {
            cleanup.run();
        } finally {
            cleanupDeadlineMillis = Long.MAX_VALUE;
        }
    }

    long cleanupGraceMillis() {
        long budgetMillis = command.stopArguments.getStopAfterTimeInSec() * 1000;
        return budgetMillis > 0 ? Math.min(MAX_CLEANUP_GRACE_MILLIS, budgetMillis / CLEANUP_GRACE_BUDGET_DIVISOR)
                : MAX_CLEANUP_GRACE_MILLIS;
    }

    private boolean isCleanupGraceExpired() {
        if (cleanupDeadlineMillis == Long.MAX_VALUE || clock.getAsLong() < cleanupDeadlineMillis) {
            return false;
        }
        if (!cleanupGraceExpiryLogged) {
            cleanupGraceExpiryLogged = true;
            logger.warning("Cleanup grace period expired; skipping the remaining deferred operations");
        }
        return true;
    }

    void runSecondPhaseFuzzers(List<FuzzingData> fuzzingData, SecondPhaseFuzzer.Trigger trigger) {
        List<Fuzzer> triggeredFuzzers = command.filterArguments.getSecondPhaseFuzzers().stream()
                .filter(fuzzer -> triggers(fuzzer).contains(trigger))
                .toList();
        runFuzzers(fuzzingData, triggeredFuzzers);
    }

    private static List<SecondPhaseFuzzer.Trigger> triggers(Fuzzer fuzzer) {
        SecondPhaseFuzzer secondPhaseFuzzer = AnnotationUtils.findAnnotation(fuzzer.getClass(), SecondPhaseFuzzer.class);
        return secondPhaseFuzzer == null ? List.of(SecondPhaseFuzzer.Trigger.PATH_COMPLETED) : List.of(secondPhaseFuzzer.triggers());
    }

    private Set<Map.Entry<String, PathItem>> sortPathsAlphabetically(OpenAPI openAPI, List<String> pathsOrder) {
        Comparator<Map.Entry<String, PathItem>> customComparator = CatsUtil.createCustomComparatorBasedOnPathsOrder(pathsOrder);
        Set<Map.Entry<String, PathItem>> pathsOrderedAlphabetically = openAPI.getPaths().entrySet()
                .stream().sorted(customComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        logger.debug("Paths ordered alphabetically: {}", pathsOrderedAlphabetically.stream().map(Map.Entry::getKey).toList());
        return pathsOrderedAlphabetically;
    }

    private void fuzzPreparedPath(PreparedPath path) {
        command.executionEventPublisher.publish(new CatsExecutionEvent.PathStarted(Instant.now(), path.pathKey()));
        logger.start(AnsiUtils.bold("Start fuzzing path {}"), path.pathKey());
        if (!path.filteredFuzzingData().isEmpty() || !path.fuzzersToRun().isEmpty()) {
            runFirstPhaseFuzzers(path.filteredFuzzingData(), path.fuzzersToRun());
            runSecondPhaseFuzzers(path.filteredFuzzingData(), SecondPhaseFuzzer.Trigger.PATH_COMPLETED);
        }
        command.executionEventPublisher.publish(new CatsExecutionEvent.PathCompleted(Instant.now(), path.pathKey()));
    }

    void runFirstPhaseFuzzers(List<FuzzingData> fuzzingData, List<Fuzzer> configuredFuzzers) {
        if (!command.processingArguments.isReuseSuccessfulResources() ||
                configuredFuzzers.stream().noneMatch(HappyPathFuzzer.class::isInstance)) {
            runFuzzers(fuzzingData, configuredFuzzers);
            return;
        }
        runSeedFuzzersForPath(fuzzingData, configuredFuzzers);
        List<Fuzzer> remainingFuzzers = nonSeedFuzzers(configuredFuzzers);
        runFuzzers(nonDeleteData(fuzzingData), remainingFuzzers);
        runDeleteOperations(deleteData(fuzzingData), remainingFuzzers);
    }

    private void runSeedFuzzersForPath(List<FuzzingData> fuzzingData, List<Fuzzer> configuredFuzzers) {
        List<Fuzzer> seedFuzzers = seedFuzzers(configuredFuzzers);
        if (seedFuzzers.isEmpty()) {
            return;
        }
        List<FuzzingData> nonDelete = nonDeleteData(fuzzingData);
        List<FuzzingData> seedData = nonDelete.stream()
                .filter(this::isSafeResourceSeed)
                .sorted(Comparator.comparingInt(data -> seedMethodOrder(data.getMethod())))
                .toList();
        List<FuzzingData> remainingData = nonDelete.stream().filter(data -> !seedData.contains(data)).toList();
        runFuzzers(seedData, seedFuzzers);
        runFuzzers(remainingData, List.of(seedFuzzers.getFirst()));
        List<FuzzingData> deletes = deleteData(fuzzingData);
        if (!deletes.isEmpty()) {
            deferredHappyPathDeletes.add(new DeferredHappyPathDelete(fuzzingData, deletes, List.of(seedFuzzers.getFirst())));
        }
    }

    private List<FuzzingData> nonDeleteData(List<FuzzingData> fuzzingData) {
        return fuzzingData.stream().filter(data -> data.getMethod() != HttpMethod.DELETE).toList();
    }

    private List<FuzzingData> deleteData(List<FuzzingData> fuzzingData) {
        return fuzzingData.stream().filter(data -> data.getMethod() == HttpMethod.DELETE).toList();
    }

    private boolean isSafeResourceSeed(FuzzingData data) {
        return data.getMethod() == HttpMethod.POST || data.getMethod() == HttpMethod.PUT ||
                data.getMethod() == HttpMethod.PATCH || data.getMethod() == HttpMethod.GET ||
                data.getMethod() == HttpMethod.HEAD;
    }

    private static int seedMethodOrder(HttpMethod method) {
        return switch (method) {
            case POST, GET -> 0;
            case PUT -> 1;
            case PATCH -> 2;
            case HEAD -> 3;
            default -> 4;
        };
    }

    private void runFuzzers(List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered, List<Fuzzer> configuredFuzzers) {
        for (Fuzzer fuzzer : configuredFuzzers) {
            CatsExecutionCancelledException.check();
            List<FuzzingData> filteredData = filterFuzzingData(fuzzingDataListWithHttpMethodsFiltered, fuzzer);
            for (FuzzingData data : filteredData) {
                CatsExecutionCancelledException.check();
                if (isCleanupGraceExpired()) {
                    return;
                }
                runSingleFuzzer(fuzzer, data);
            }
        }
    }

    private void runSingleFuzzer(Fuzzer fuzzer, FuzzingData data) {
        runSingleFuzzer(fuzzer, data, fuzzer::fuzz);
    }

    private void runSingleFuzzer(Fuzzer fuzzer, FuzzingData data, Consumer<FuzzingData> action) {
        CatsExecutionCancelledException.check();
        if (data.shouldSkipFuzzerForPath(fuzzer.toString())) {
            logger.skip("Skipping Fuzzer {} for path {} due to OpenAPI extension configuration",
                    AnsiUtils.yellow(fuzzer.toString()), data.getPath());
            return;
        }

        logFuzzerStart(fuzzer, data);

        boolean reportsFuzzerLifecycle = !(fuzzer instanceof FunctionalFuzzer)
                && !(fuzzer instanceof FunctionalFlowFuzzer);
        if (reportsFuzzerLifecycle) {
            command.testCaseListener.beforeFuzz(fuzzer.getClass(), data.getContractPath(), data.getMethod().name());
        }

        try {
            action.accept(data);
        } finally {
            if (reportsFuzzerLifecycle) {
                command.testCaseListener.afterFuzz(data.getContractPath());
            }
        }

        CatsExecutionCancelledException.check();
        logFuzzerEnd(fuzzer, data);
    }

    private void logFuzzerEnd(Fuzzer fuzzer, FuzzingData data) {
        logger.complete("Finishing Fuzzer {}, http method {}, path {}", AnsiUtils.green(fuzzer.toString()), data.getMethod(), data.getPath());
        logger.info("{}", SEPARATOR);
    }

    private void logFuzzerStart(Fuzzer fuzzer, FuzzingData data) {
        logger.start("Starting Fuzzer {}, http method {}, path {}", AnsiUtils.green(fuzzer.toString()), data.getMethod(), data.getPath());
        logger.debug("Fuzzing payload: {}", data.getPayload());
    }

    private List<FuzzingData> filterFuzzingData(List<FuzzingData> fuzzingDataListWithHttpMethodsFiltered, Fuzzer fuzzer) {
        List<FuzzingData> methodCompatible = CatsUtil.filterAndPrintNotMatching(
                fuzzingDataListWithHttpMethodsFiltered,
                data -> !fuzzer.skipForHttpMethods().contains(data.getMethod()),
                logger,
                "HTTP method {} is not supported by {}",
                t -> t.getMethod().toString(), fuzzer.toString());
        return CatsUtil.filterAndPrintNotMatching(
                methodCompatible,
                fuzzer::isApplicableTo,
                logger,
                "Payload format {} is not supported by {}",
                data -> data.getPayloadFormat().name(), fuzzer.toString());
    }
}
