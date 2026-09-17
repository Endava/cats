package com.endava.cats.aop;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.auth.wfc.WfcAuthProvider;
import com.endava.cats.execution.ExecutionSummaryProvider;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.VersionChecker;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Aspect used to suspend CATS logic when running in dryRun mode.
 * The aspect will suppress all calls to the service and any reporting.
 * As Quarkus does not support true AOP, to keep the code as clean as possible @DryRun was used to annotate
 * classes which are required to suspend their execution.
 */
@DryRun
@Interceptor
public class DryRunAspect {
    private static final Object PROCEED = new Object();
    private static volatile boolean dryRun;

    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    /**
     * Holds number of tests executed per path + http method.
     */
    private final Map<String, Integer> paths = new TreeMap<>();

    @Inject
    FilterArguments filterArguments;

    @Inject
    ReportingArguments reportingArguments;

    @Inject
    ExecutionSummaryProvider executionSummaryProvider;

    public static boolean isDryRun() {
        return dryRun;
    }

    /**
     * Intercepts the startSession from the TestCaseListener.
     *
     * @param context invocation context
     * @return result of the real method
     * @throws Exception if something goes wrong
     */
    public Object startSession(InvocationContext context) throws Exception {
        dryRun = true;
        paths.clear();
        if (reportingArguments.isJsonOutput()) {
            CatsUtil.setCatsLogLevel("OFF");
        }
        Object result = context.proceed();
        CatsUtil.setCatsLogLevel("OFF");
        return result;
    }

    /**
     * Doesn't do anything.
     *
     * @return empty CatsResponse
     */
    public Object dontInvokeService() {
        return CatsResponse.empty();
    }

    /**
     * Prevents test files from being written.
     *
     * @return null
     */
    public Object dontWriteTestCase() {
        return null;
    }

    /**
     * Logic to be executed instead of TestCaseListener.endSession()
     *
     * @return the final execution summary, without writing report files
     */
    public ExecutionSummary endSession() {
        try {
            if (reportingArguments.isJsonOutput()) {
                List<DryRunEntry> pathTests = paths.entrySet().stream()
                        .map(entry -> {
                            int splitIndex = entry.getKey().lastIndexOf("_");
                            String path = entry.getKey().substring(0, splitIndex);
                            String httpMethod = entry.getKey().substring(splitIndex + 1);

                            return new DryRunEntry(path, httpMethod, String.valueOf(entry.getValue()));
                        })
                        .toList();
                logger.noFormat(JsonUtils.GSON.toJson(pathTests));
            } else {
                logger.noFormat("\n");
                CatsUtil.setCatsLogLevel("INFO");
                logger.noFormat("Number of tests that will be run with this configuration: {}", paths.values().stream().reduce(0, Integer::sum));
                paths.forEach((s, integer) -> logger.noFormat(AnsiUtils.boldYellow(" -> path {}: {} tests"), s, integer));
            }
            return executionSummaryProvider.snapshot();
        } finally {
            dryRun = false;
        }
    }

    /**
     * Logic to be executed instead of TestCaseListener.reportXXX methods.
     *
     * @param context invocation context
     * @return nothing
     */
    public Object report(InvocationContext context) {
        Object data = context.getParameters()[1];

        if (data instanceof FuzzingData fuzzingData) {
            paths.merge(fuzzingData.getPath() + "_" + fuzzingData.getMethod(), 1, Integer::sum);
        }
        return null;
    }

    private Object suppressExternalSideEffect(InvocationContext context) {
        Class<?> declaringClass = context.getMethod().getDeclaringClass();
        String methodName = context.getMethod().getName();
        if (declaringClass == VersionChecker.class && "checkForNewVersion".equals(methodName)) {
            return VersionChecker.CheckResult.builder().build();
        }
        if (declaringClass == WfcAuthProvider.class) {
            if ("getHeaders".equals(methodName) || "getQueryParams".equals(methodName)) {
                return Map.of();
            }
            if ("applyQueryParams".equals(methodName)) {
                return context.getParameters()[0];
            }
        }
        return PROCEED;
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) {
            return null;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return 0;
    }

    /**
     * Intercepts all calls annotated with DryRun
     *
     * @param context invocation context
     * @return mostly nothing
     * @throws Exception in case something happens
     */
    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        if (!filterArguments.isDryRun()) {
            return context.proceed();
        }

        Object suppressed = suppressExternalSideEffect(context);
        if (suppressed != PROCEED) {
            return suppressed;
        }

        String methodName = context.getMethod().getName();
        return switch (methodName) {
            case String s when s.startsWith("report") -> report(context);
            case String s when s.startsWith("endSession") -> endSession();
            case String s when s.startsWith("startSession") -> startSession(context);
            case String s when s.startsWith("call") -> dontInvokeService();
            case String s when s.startsWith("writeTestCase") -> dontWriteTestCase();
            case String s when s.startsWith("getErrors") ||
                    s.startsWith("initReportingPath") ||
                    s.startsWith("renderFuzzingHeader") ||
                    s.startsWith("notifySummaryObservers") -> defaultValue(context.getMethod().getReturnType());
            default -> context.proceed();
        };
    }
}
