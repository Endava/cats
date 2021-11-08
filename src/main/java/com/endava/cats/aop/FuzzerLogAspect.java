package com.endava.cats.aop;

import com.endava.cats.util.ConsoleUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.fusesource.jansi.Ansi;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Aspect used to prefix logging with the right Fuzzer name. In order for it to work, all Fuzzers must implement the
 * {@link com.endava.cats.fuzzer.Fuzzer} interface.
 */
@Aspect
@Component
public class FuzzerLogAspect {

    /**
     * Adds the Fuzzer name into the MDC context.
     * <p>
     * In order to make log lines shorter, it will only retain the capital letters from the Fuzzer name.
     *
     * @param joinPoint the current join point
     * @return the execution result
     * @throws Throwable in case something goes wrong
     */
    @Around("execution(* com.endava.cats.fuzzer.Fuzzer.fuzz(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String clazz = joinPoint.getTarget().getClass().getSimpleName().replaceAll("[a-z]", "");
        MDC.put("fuzzer", ConsoleUtils.centerWithAnsiColor(clazz, 5, Ansi.Color.MAGENTA));
        MDC.put("fuzzerKey", joinPoint.getTarget().getClass().getSimpleName());
        Object ret = joinPoint.proceed();
        MDC.put("fuzzer", null);
        MDC.put("fuzzerKey", null);
        return ret;
    }

}
