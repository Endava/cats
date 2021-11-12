package com.endava.cats.aop;

import ch.qos.logback.classic.Level;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Aspect used to suspend CATS logic when running in dryRun mode.
 * The aspect will suppress all calls to the service and any reporting.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "dryRun", havingValue = "")
public class DryRunAspect {

    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(DryRunAspect.class);
    private final Map<String, Integer> paths = new TreeMap<>();
    private int counter;

    /**
     * We suppress logging when doing a dry run.
     *
     * @param joinPoint the AspectJ join point
     * @return "OFF" of the actual log level, depending on the --dryRun arg
     */
    @Around("execution(* com.endava.cats.args.ReportingArguments.getReportingLevel())")
    public Object reportingLevel(ProceedingJoinPoint joinPoint) {
        return "OFF";
    }

    @Around("execution(* com.endava.cats.io.ServiceCaller.call(..))")
    public Object dryRunDontInvokeService(ProceedingJoinPoint joinPoint) {
        return CatsResponse.empty();
    }

    @Around("execution(* com.endava.cats.io.TestCaseExporter.writeTestCase(..))")
    public Object dryRunDontWriteTestCase(ProceedingJoinPoint joinPoint) {
        return null;
    }

    @Around("execution(* com.endava.cats.report.TestCaseListener.endSession(..))")
    public Object endSession(ProceedingJoinPoint joinPoint) {
        System.out.print("\n");
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.endava.cats")).setLevel(Level.INFO);
        LOGGER.note("Number of tests that will be run with this configuration: {}", paths.values().stream().reduce(0, Integer::sum));
        paths.forEach((s, integer) -> LOGGER.star(ansi().fgBrightYellow().bold().a(" -> path {}: {} tests").toString(), s, integer));
        return null;
    }

    @Around("execution(* com.endava.cats.report.TestCaseListener.reportInfo(..)) || execution(* com.endava.cats.report.TestCaseListener.reportResult(..))")
    public Object report(ProceedingJoinPoint joinPoint) {
        Object data = joinPoint.getArgs()[1];
        if (data instanceof FuzzingData) {
            if (counter % 10000 == 0) {
                System.out.print(".");
            }
            paths.merge(((FuzzingData) data).getPath(), 1, Integer::sum);
        } else {
            paths.merge("contract-level", 1, Integer::sum);
        }
        counter++;
        return null;
    }
}
