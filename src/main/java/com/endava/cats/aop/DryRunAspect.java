package com.endava.cats.aop;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Aspect used to suspend CATS logic when running in dryRun mode.
 * The aspect will suppress all calls to the service and any reporting.
 * As Quarkus does not support true AOP, to keep the code as clean as possible @DryRun was used to annotate
 * classes which are required to suspend their execution.
 */
@DryRun
@Interceptor
public class DryRunAspect {

    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    private final Map<String, Integer> paths = new TreeMap<>();
    @Inject
    FilterArguments filterArguments;

    @Inject
    ReportingArguments reportingArguments;

    private int counter;

    public Object startSession(InvocationContext context) throws Exception {
        if (reportingArguments.isJson()) {
            CatsUtil.setCatsLogLevel("OFF");
        }
        Object result = context.proceed();
        CatsUtil.setCatsLogLevel("OFF");
        return result;
    }

    public Object dontInvokeService() {
        return CatsResponse.empty();
    }

    public Object dontWriteTestCase() {
        return null;
    }

    /**
     * Logic to be executed instead of TestCaseListener.endSession()
     *
     * @return nothing
     */
    public Object endSession() {
        if (reportingArguments.isJson()) {
            List<DryRunEntry> pathTests = paths.entrySet().stream()
                    .map(entry -> new DryRunEntry(entry.getKey().substring(0, entry.getKey().lastIndexOf("_")),
                            entry.getKey().substring(entry.getKey().lastIndexOf("_") + 1),
                            String.valueOf(entry.getValue())))
                    .toList();
            logger.noFormat(JsonUtils.GSON.toJson(pathTests));
        } else {
            logger.noFormat("\n");
            CatsUtil.setCatsLogLevel("INFO");
            logger.info("Number of tests that will be run with this configuration: {}", paths.values().stream().reduce(0, Integer::sum));
            paths.forEach((s, integer) -> logger.star(ansi().fgBrightYellow().bold().a(" -> path {}: {} tests").toString(), s, integer));
        }
        return null;
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
            if (counter % 10000 == 0 && !reportingArguments.isJson()) {
                logger.noFormat(StringUtils.repeat("..", 1 + (counter / 10000)));
            }
            paths.merge(fuzzingData.getPath() + "_" + fuzzingData.getMethod(), 1, Integer::sum);
        }
        counter++;
        return null;
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
        if (filterArguments.isDryRun()) {
            if (context.getMethod().getName().startsWith("report")) {
                return report(context);
            }
            if (context.getMethod().getName().startsWith("endSession")) {
                return endSession();
            }
            if (context.getMethod().getName().startsWith("startSession")) {
                return startSession(context);
            }
            if (context.getMethod().getName().startsWith("call")) {
                return dontInvokeService();
            }
            if (context.getMethod().getName().startsWith("getErrors")) {
                return 0;
            }
            if (context.getMethod().getName().startsWith("writeTestCase")) {
                return dontWriteTestCase();
            }
            if (context.getMethod().getName().startsWith("initReportingPath")) {
                return 0;
            }
        }
        return context.proceed();
    }
}
